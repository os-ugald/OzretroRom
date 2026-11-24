from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WRAPPER = ROOT / "gradlew.bat"


def main() -> None:
    text = WRAPPER.read_text(encoding="utf-8")

    required = {
        "JAVA_HOME branch": "if defined JAVA_HOME",
        "JAVA_HOME executable": 'set "JAVA_EXE=%JAVA_HOME%\\bin\\java.exe"',
        "invalid JAVA_HOME guard": 'if not exist "%JAVA_HOME%\\bin\\java.exe"',
        "PATH fallback": 'set "JAVA_EXE=java.exe"',
        "resolved Java launch": '"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS%',
        "Gradle exit propagation": "endlocal & exit /b %EXIT_CODE%",
    }
    missing = [label for label, token in required.items() if token not in text]
    if missing:
        raise AssertionError(f"gradlew.bat is missing: {', '.join(missing)}")

    if "\njava %JAVA_OPTS% %GRADLE_OPTS%" in text.replace("\r\n", "\n"):
        raise AssertionError("gradlew.bat still launches an unresolved java command")

    print("windows_wrapper_java_home=pass")


if __name__ == "__main__":
    main()
