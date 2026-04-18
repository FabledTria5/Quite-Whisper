use crate::prompt::PromptBuilder;
use crate::settings::AppSettings;
use std::path::{Path, PathBuf};
use whisper_rs::{FullParams, SamplingStrategy, WhisperContext, WhisperContextParameters};

#[derive(Default)]
pub struct SpeechEngine {
    loaded_model: Option<PathBuf>,
    context: Option<WhisperContext>,
    post_processor: BasicTextPostProcessor,
}

impl SpeechEngine {
    pub fn transcribe(
        &mut self,
        model_path: &Path,
        samples_16khz: &[f32],
        settings: &AppSettings,
    ) -> anyhow::Result<String> {
        if samples_16khz.is_empty() {
            anyhow::bail!("Recording contains only silence");
        }

        self.ensure_model_loaded(model_path)?;
        let context = self
            .context
            .as_ref()
            .ok_or_else(|| anyhow::anyhow!("Whisper model is not loaded"))?;

        let mut params = FullParams::new(SamplingStrategy::Greedy { best_of: 1 });
        params.set_language(Some("ru"));
        params.set_translate(false);
        params.set_print_special(false);
        params.set_print_progress(false);
        params.set_print_realtime(false);
        params.set_print_timestamps(false);
        params.set_initial_prompt(&PromptBuilder::build(settings));

        let mut state = context.create_state()?;
        state.full(params, samples_16khz)?;

        let segment_count = state.full_n_segments();
        let mut text = String::new();
        for index in 0..segment_count {
            let segment = state
                .get_segment(index)
                .ok_or_else(|| anyhow::anyhow!("Whisper segment {index} is out of bounds"))?;
            text.push_str(segment.to_str_lossy()?.as_ref());
            text.push(' ');
        }

        self.post_processor.process(&text)
    }

    fn ensure_model_loaded(&mut self, model_path: &Path) -> anyhow::Result<()> {
        if self.loaded_model.as_deref() == Some(model_path) && self.context.is_some() {
            return Ok(());
        }

        if !model_path.exists() {
            anyhow::bail!("Whisper model was not found: {}", model_path.display());
        }

        let context = WhisperContext::new_with_params(
            model_path.to_string_lossy().as_ref(),
            WhisperContextParameters::default(),
        )?;
        self.loaded_model = Some(model_path.to_path_buf());
        self.context = Some(context);
        Ok(())
    }
}

pub trait TextPostProcessor {
    fn process(&self, text: &str) -> anyhow::Result<String>;
}

#[derive(Default)]
pub struct BasicTextPostProcessor;

impl TextPostProcessor for BasicTextPostProcessor {
    fn process(&self, text: &str) -> anyhow::Result<String> {
        let mut cleaned = text.split_whitespace().collect::<Vec<_>>().join(" ");

        for repeated in ["..", ",,", "!!", "??"] {
            while cleaned.contains(repeated) {
                let replacement = &repeated[0..1];
                cleaned = cleaned.replace(repeated, replacement);
            }
        }

        Ok(cleaned.trim().to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn post_processor_normalizes_spacing_and_repeated_punctuation() {
        let processor = BasicTextPostProcessor;

        let result = processor
            .process("  Привет   Kotlin,,  это тест!! ")
            .unwrap();

        assert_eq!(result, "Привет Kotlin, это тест!");
    }
}
