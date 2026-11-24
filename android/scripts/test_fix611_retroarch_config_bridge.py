from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
retro = (ROOT / 'app/src/main/java/cl/gakuto/kizuna/launch/RetroArchManager.kt').read_text(encoding='utf-8')
checks = {
    'config_extra_constant': 'const val EXTRA_CONFIGFILE = "CONFIGFILE"' in retro,
    'config_filename_constant': 'const val RETROARCH_CONFIG_FILE = "retroarch.cfg"' in retro,
    'default_config_helper': 'fun defaultConfigPath(packageName: String): String' in retro,
    'target_package_context': 'createPackageContext(packageName, 0).getExternalFilesDir(null)' in retro,
    'scoped_storage_fallback': '"Android/data/$packageName/files"' in retro,
    'intent_config_extra': 'putExtra(EXTRA_CONFIGFILE, configFile)' in retro,
    'does_not_write_config': 'FileOutputStream(configFile)' not in retro and 'writeText(' not in retro,
    'core_full_path_preserved': 'File(targetInfo.dataDir, "cores/$coreName")' in retro,
    'rom_extra_preserved': 'putExtra(EXTRA_ROM, romPath)' in retro,
    'data_dir_preserved': 'putExtra("DATADIR", dataDir)' in retro,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('FIX611_RETROARCH_CONFIG_BRIDGE_FAILED ' + ','.join(failed))
print(f'FIX611_RETROARCH_CONFIG_BRIDGE_OK checks={len(checks)}')
