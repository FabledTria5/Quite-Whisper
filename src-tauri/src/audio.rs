use cpal::{
    traits::{DeviceTrait, HostTrait, StreamTrait},
    SampleFormat, Stream,
};
use serde::Serialize;
use std::sync::{Arc, Mutex};

pub const TARGET_SAMPLE_RATE: u32 = 16_000;
const SILENCE_THRESHOLD: f32 = 0.008;
const MIN_RECORDING_SAMPLES: usize = TARGET_SAMPLE_RATE as usize / 5;

#[derive(Debug, Clone)]
pub struct CapturedAudio {
    pub samples: Vec<f32>,
    pub sample_rate: u32,
}

impl CapturedAudio {
    pub fn normalized_16khz(self) -> Vec<f32> {
        let resampled = resample_linear(&self.samples, self.sample_rate, TARGET_SAMPLE_RATE);
        trim_silence(&resampled, TARGET_SAMPLE_RATE)
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct MicrophoneStatus {
    pub default_device: Option<String>,
    pub devices: Vec<String>,
}

#[derive(Default)]
pub struct AudioRecorder {
    active: Option<ActiveRecording>,
}

struct ActiveRecording {
    stream: Stream,
    samples: Arc<Mutex<Vec<f32>>>,
    sample_rate: u32,
}

impl AudioRecorder {
    pub fn start(&mut self, requested_device: Option<&str>) -> anyhow::Result<()> {
        if self.active.is_some() {
            return Ok(());
        }

        let host = cpal::default_host();
        let device = select_input_device(&host, requested_device)?;
        let supported_config = device.default_input_config()?;
        let sample_format = supported_config.sample_format();
        let config: cpal::StreamConfig = supported_config.into();
        let channels = config.channels as usize;
        let sample_rate = config.sample_rate.0;
        let samples = Arc::new(Mutex::new(Vec::<f32>::new()));
        let buffer = Arc::clone(&samples);
        let err_fn = |err| eprintln!("input stream error: {err}");

        let stream = match sample_format {
            SampleFormat::F32 => device.build_input_stream(
                &config,
                move |data: &[f32], _| push_f32_samples(data, channels, &buffer),
                err_fn,
                None,
            )?,
            SampleFormat::I16 => device.build_input_stream(
                &config,
                move |data: &[i16], _| push_i16_samples(data, channels, &buffer),
                err_fn,
                None,
            )?,
            SampleFormat::U16 => device.build_input_stream(
                &config,
                move |data: &[u16], _| push_u16_samples(data, channels, &buffer),
                err_fn,
                None,
            )?,
            other => anyhow::bail!("Unsupported input sample format: {other:?}"),
        };

        stream.play()?;
        self.active = Some(ActiveRecording {
            stream,
            samples,
            sample_rate,
        });
        Ok(())
    }

    pub fn stop(&mut self) -> anyhow::Result<CapturedAudio> {
        let active = self
            .active
            .take()
            .ok_or_else(|| anyhow::anyhow!("Recording is not active"))?;
        drop(active.stream);

        let samples = active
            .samples
            .lock()
            .map_err(|_| anyhow::anyhow!("Audio buffer lock is poisoned"))?
            .clone();

        if samples.len() < MIN_RECORDING_SAMPLES {
            anyhow::bail!("Recording is too short");
        }

        Ok(CapturedAudio {
            samples,
            sample_rate: active.sample_rate,
        })
    }
}

pub fn microphone_status() -> anyhow::Result<MicrophoneStatus> {
    let host = cpal::default_host();
    let default_device = host
        .default_input_device()
        .and_then(|device| device.name().ok());
    let devices = host
        .input_devices()?
        .filter_map(|device| device.name().ok())
        .collect();

    Ok(MicrophoneStatus {
        default_device,
        devices,
    })
}

fn select_input_device(
    host: &cpal::Host,
    requested_device: Option<&str>,
) -> anyhow::Result<cpal::Device> {
    if let Some(requested_device) = requested_device {
        if let Some(device) = host
            .input_devices()?
            .find(|device| device.name().ok().as_deref() == Some(requested_device))
        {
            return Ok(device);
        }
    }

    host.default_input_device()
        .ok_or_else(|| anyhow::anyhow!("No default input device is available"))
}

fn push_f32_samples(data: &[f32], channels: usize, output: &Arc<Mutex<Vec<f32>>>) {
    push_downmixed(data.chunks(channels).map(average_frame), output);
}

fn push_i16_samples(data: &[i16], channels: usize, output: &Arc<Mutex<Vec<f32>>>) {
    push_downmixed(
        data.chunks(channels)
            .map(|frame| frame.iter().map(|sample| *sample as f32 / i16::MAX as f32).sum::<f32>() / frame.len() as f32),
        output,
    );
}

fn push_u16_samples(data: &[u16], channels: usize, output: &Arc<Mutex<Vec<f32>>>) {
    push_downmixed(
        data.chunks(channels).map(|frame| {
            frame
                .iter()
                .map(|sample| (*sample as f32 / u16::MAX as f32) * 2.0 - 1.0)
                .sum::<f32>()
                / frame.len() as f32
        }),
        output,
    );
}

fn push_downmixed(samples: impl Iterator<Item = f32>, output: &Arc<Mutex<Vec<f32>>>) {
    if let Ok(mut output) = output.lock() {
        output.extend(samples);
    }
}

fn average_frame(frame: &[f32]) -> f32 {
    frame.iter().sum::<f32>() / frame.len() as f32
}

pub fn resample_linear(samples: &[f32], source_rate: u32, target_rate: u32) -> Vec<f32> {
    if samples.is_empty() || source_rate == target_rate {
        return samples.to_vec();
    }

    let ratio = source_rate as f64 / target_rate as f64;
    let target_len = (samples.len() as f64 / ratio).round().max(1.0) as usize;
    let mut output = Vec::with_capacity(target_len);

    for index in 0..target_len {
        let source_pos = index as f64 * ratio;
        let left = source_pos.floor() as usize;
        let right = (left + 1).min(samples.len() - 1);
        let fraction = (source_pos - left as f64) as f32;
        output.push(samples[left] * (1.0 - fraction) + samples[right] * fraction);
    }

    output
}

pub fn trim_silence(samples: &[f32], sample_rate: u32) -> Vec<f32> {
    let Some(first) = samples
        .iter()
        .position(|sample| sample.abs() >= SILENCE_THRESHOLD)
    else {
        return Vec::new();
    };

    let last = samples
        .iter()
        .rposition(|sample| sample.abs() >= SILENCE_THRESHOLD)
        .unwrap_or(first);
    let padding = (sample_rate as usize / 20).max(1);
    let start = first.saturating_sub(padding);
    let end = (last + padding).min(samples.len().saturating_sub(1));

    samples[start..=end].to_vec()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resample_downsamples_to_target_rate() {
        let source = vec![0.5; 48_000];
        let result = resample_linear(&source, 48_000, 16_000);

        assert_eq!(result.len(), 16_000);
    }

    #[test]
    fn trim_silence_keeps_non_silent_region_with_padding() {
        let mut source = vec![0.0; 1000];
        source[500] = 0.2;

        let result = trim_silence(&source, 1000);

        assert!(result.len() > 1);
        assert!(result.contains(&0.2));
    }
}
