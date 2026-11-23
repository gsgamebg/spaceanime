"""Install verified build tools locally; requires Python 3 and internet access."""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".tools"
TOOLS.mkdir(exist_ok=True)
opener = urllib.request.build_opener()
opener.addheaders = [("User-Agent", "Mozilla/5.0 Anim-Setup")]
urllib.request.install_opener(opener)


def read(url):
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read()


def install(name, url, digest, algorithm, destination):
    marker = destination / ".installed"
    if marker.exists() and marker.read_text() == digest:
        print(f"{name}: ready", flush=True)
        return
    archive = TOOLS / f"{name}.zip"
    print(f"Downloading {name}...", flush=True)
    cached_digest = None
    if archive.exists():
        with archive.open("rb") as cached:
            cached_digest = hashlib.file_digest(cached, algorithm).hexdigest()
    if cached_digest != digest:
        with urllib.request.urlopen(url, timeout=120) as response, archive.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
    with archive.open("rb") as source:
        actual = hashlib.file_digest(source, algorithm).hexdigest()
    if actual != digest:
        raise RuntimeError(f"Checksum mismatch: {name}")
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as package:
        for member in package.infolist():
            if not (destination / member.filename).resolve().is_relative_to(destination.resolve()):
                raise RuntimeError("Unsafe archive path")
        package.extractall(destination)
    marker.write_text(digest)
    print(f"{name}: installed and verified", flush=True)


jdk = json.loads(read("https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows"))[0]["binary"]["package"]
gradle_sha = read("https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256").decode().strip()
repository = ET.fromstring(read("https://dl.google.com/android/repository/repository2-1.xml"))
candidates = []
for package in repository.findall("remotePackage"):
    if not package.get("path", "").startswith("cmdline-tools;"):
        continue
    channel = package.find("channelRef")
    if channel is not None and channel.get("ref") != "channel-0":
        continue
    for archive in package.findall("archives/archive"):
        if archive.findtext("host-os") == "windows":
            candidates.append((int(package.findtext("revision/major", "0")), archive.find("complete")))
# Pin CLI 19 for reproducible sdkmanager behavior with this Gradle/AGP baseline.
_, sdk = next(item for item in candidates if item[0] == 19)
checksum = sdk.find("checksum")
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    jobs = [
        pool.submit(install, "jdk", jdk["link"], jdk["checksum"], "sha256", TOOLS / "jdk"),
        pool.submit(install, "gradle", "https://services.gradle.org/distributions/gradle-8.13-bin.zip", gradle_sha, "sha256", TOOLS / "gradle"),
        pool.submit(install, "android-cli", "https://dl.google.com/android/repository/" + sdk.findtext("url"), checksum.text.strip(), checksum.get("type", "sha1"), TOOLS / "android-cli"),
    ]
    for job in jobs:
        job.result()
(TOOLS / "android-sdk").mkdir(exist_ok=True)
(ROOT / "local.properties").write_text("sdk.dir=" + (TOOLS / "android-sdk").as_posix() + "\n")
print("Build tools ready. Android platform packages must be installed with sdkmanager.", flush=True)
