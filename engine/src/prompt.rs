use crate::settings::AppSettings;

const BASE_PROMPT: &str = "Это техническая диктовка на русском языке. В тексте могут встречаться английские названия технологий, API, библиотек, классов, функций и команд.";

pub struct PromptBuilder;

impl PromptBuilder {
    pub fn build(settings: &AppSettings) -> String {
        let mut parts = vec![BASE_PROMPT.to_string()];

        if !settings.glossary_terms.is_empty() {
            let glossary = settings
                .glossary_terms
                .iter()
                .map(|term| term.trim())
                .filter(|term| !term.is_empty())
                .collect::<Vec<_>>()
                .join(", ");

            if !glossary.is_empty() {
                parts.push(format!("Ожидаемые термины: {glossary}."));
            }
        }

        parts.join(" ")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prompt_includes_base_guidance_and_glossary() {
        let settings = AppSettings {
            glossary_terms: vec!["Jetpack Compose".to_string(), "CTranslate2".to_string()],
            ..AppSettings::default()
        };

        let prompt = PromptBuilder::build(&settings);

        assert!(prompt.contains("техническая диктовка"));
        assert!(prompt.contains("Jetpack Compose"));
        assert!(prompt.contains("CTranslate2"));
    }
}
