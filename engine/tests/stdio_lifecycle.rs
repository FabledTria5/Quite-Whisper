use std::{
    process::{Command, Stdio},
    thread,
    time::{Duration, Instant},
};

#[test]
fn engine_exits_when_stdin_is_closed() {
    let mut child = Command::new(env!("CARGO_BIN_EXE_quite-whisper-engine"))
        .stdin(Stdio::piped())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .expect("start engine");

    drop(child.stdin.take());

    let deadline = Instant::now() + Duration::from_secs(2);
    loop {
        match child.try_wait().expect("poll engine") {
            Some(status) => {
                assert!(status.success(), "engine exited with {status}");
                return;
            }
            None if Instant::now() >= deadline => {
                let _ = child.kill();
                let _ = child.wait();
                panic!("engine did not exit after stdin was closed");
            }
            None => thread::sleep(Duration::from_millis(25)),
        }
    }
}
