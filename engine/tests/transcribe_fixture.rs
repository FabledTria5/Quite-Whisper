use quite_whisper_engine::{settings::AppSettings, speech::SpeechEngine};
use std::{env, path::PathBuf};

#[test]
fn transcribe_fixture_when_model_and_wav_are_provided() {
    let model_path = match env::var("QUITEWHISPER_TEST_MODEL") {
        Ok(path) => PathBuf::from(path),
        Err(_) => return,
    };
    let wav_path = match env::var("QUITEWHISPER_TEST_WAV") {
        Ok(path) => PathBuf::from(path),
        Err(_) => return,
    };

    assert!(
        model_path.exists(),
        "QUITEWHISPER_TEST_MODEL must point to an existing ggml model"
    );
    assert!(
        wav_path.exists(),
        "QUITEWHISPER_TEST_WAV must point to an existing 16 kHz mono WAV"
    );

    let samples = read_16khz_mono_wav(&wav_path);
    let mut engine = SpeechEngine::default();
    let text = engine
        .transcribe(&model_path, &samples, &AppSettings::default())
        .expect("fixture transcription should succeed");

    assert!(
        !text.trim().is_empty(),
        "fixture transcription should not be empty"
    );
}

fn read_16khz_mono_wav(path: &PathBuf) -> Vec<f32> {
    let mut reader = hound::WavReader::open(path).expect("fixture WAV should open");
    let spec = reader.spec();
    assert_eq!(spec.channels, 1, "fixture WAV must be mono");
    assert_eq!(spec.sample_rate, 16_000, "fixture WAV must be 16 kHz");

    match spec.sample_format {
        hound::SampleFormat::Float => reader
            .samples::<f32>()
            .map(|sample| sample.expect("valid float sample"))
            .collect(),
        hound::SampleFormat::Int => reader
            .samples::<i16>()
            .map(|sample| sample.expect("valid i16 sample") as f32 / i16::MAX as f32)
            .collect(),
    }
}
