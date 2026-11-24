# Oz RetroRom 0.6.0 Beta 3 — Build Fix 2

## Corrección

La migración Room 3 → 4 declaraba `lastPlayedAt` sin valor predeterminado, mientras `GameEntity` exige `@ColumnInfo(defaultValue = "NULL")`.

Se cambió:

```sql
ALTER TABLE games ADD COLUMN lastPlayedAt INTEGER
```

por:

```sql
ALTER TABLE games ADD COLUMN lastPlayedAt INTEGER DEFAULT NULL
```

También se eliminó `fallbackToDestructiveMigration()` para impedir borrados silenciosos si falta una migración futura.

## Datos

La migración conserva juegos, colecciones, favoritos, historial local y configuración. No requiere borrar datos ni desinstalar la aplicación.

## Verificación

```bash
python scripts/validate_project.py
python scripts/test_room_migration_3_4.py
./gradlew :app:assembleDebug --no-daemon --max-workers=1 --stacktrace
```
