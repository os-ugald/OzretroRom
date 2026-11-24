# MiNERVA Nintendo metadata census

This utility joins MiNERVA's public dashboard torrent metadata with its browse listings. It requests metadata pages only: it does not open ROM links, download payloads, or start torrents.

```powershell
cd tools/source_index/census
python -m unittest -v test_census.py
python census.py --output ../../../.artifacts/minerva-nintendo-census
```

If a bundled Python runtime lacks a usable CA store, use `--transport curl`; TLS verification remains enabled through the host curl trust store.

Unknown provider facts remain the string `UNKNOWN`; they are never converted to numeric zero. Generated census outputs live under `.artifacts/` and are not source inputs.

The global first pass consumes a previously captured dashboard snapshot and performs no network access:

```powershell
python -m unittest -v test_global_census.py
python global_census.py --snapshot ../../../.artifacts/minerva-global-census/raw/<snapshot>.json --output ../../../.artifacts/minerva-global-census --retrieval-ms <measured-curl-ms>
```
