🎮 PLATFORM LAB — NEO GEO LAUNCH-MODE REQUALIFICATION

WORK_ID: PLATFORM-FACTORY-V1.NEO-GEO-LAUNCH-MODE-REQUALIFICATION-01
RISK: R2
Workspace: 🎮 Oz Platform Lab — Systems & Emulation
Scope: launch-mode/integration requalification only.
Repository/code changes: NONE.
BEST EMULATOR: NOT SELECTED.
OZ support / certification: NOT DECLARED.

La petición pertenece directamente al charter de Platform Lab: cualificación de cores/emuladores, contratos de first play y preparación de evidencia; cualquier conclusión R2 vuelve a Forge y Platform Lab no modifica arquitectura ni certifica soporte.

Executive conclusion

La nueva evidencia física cambia materialmente la cualificación:

Ni Geolith Libretro ni FinalBurn Neo Libretro tienen hoy un mecanismo Oz probado para seleccionar AES/MVS de forma automática, aislada y determinista por lanzamiento.

Ambos emuladores soportan los modos upstream, pero en sus rutas Libretro la elección relevante está expuesta mediante core options. Dado que A1/A2 han demostrado físicamente que Oz no dispone actualmente de un transporte dinámico aislado probado para esas opciones, esa capacidad upstream no equivale a capacidad de integración Oz.

MAME presenta una arquitectura distinta y potencialmente mejor para este problema concreto: upstream modela AES y MVS como sistemas/máquinas seleccionables, y MAME admite selección de sistema/software desde su contrato de lanzamiento, sin necesitar un “Neo Geo mode” core option. Sin embargo, todavía no existe evidencia de que Oz tenga hoy un contrato Android/MAME verificado que pueda utilizarlo. El MAME Libretro completo tiene además una incidencia Android actual abierta relacionada con launch de software no-arcade mediante .cmd.

Por tanto:

RECOMMENDATION: B

Continuar la cualificación con ejecuciones físicas AES y MVS separadas mediante un harness manual/no-productivo acotado, manteniendo OZ INTEGRATION = BLOCKED, y llevar a Forge la posible investigación estrecha del contrato externo MAME/MAME4droid.

Esto no selecciona MAME, ni Geolith, ni FBNeo como BEST EMULATOR.

EVIDENCE
E1 — Nuevo hecho físico de Oz

Según la evidencia física aportada para Android 15 + RetroArch 1.22.2_GIT:

A1 CONFIGFILE/core_options_path → FAIL
A2 content-specific <game>.opt → FAIL

A2 llegó a ser descubierto por RetroArch, pero el valor solicitado no alcanzó al core: GET_VARIABLE conservó el baseline. El comportamiento fue repetible y Forge cerró A1/A2 para este piloto.

Consecuencia factual para esta recalificación:

Oz-controlled isolated dynamic RetroArch core option transport = NOT PROVEN / unavailable for current product integration

Esto no significa que RetroArch sea incapaz en general de manejar opciones por contenido; la propia documentación de Libretro describe los .opt. Significa que esa vía no funciona como contrato Oz probado en el entorno físico evaluado.

E2 — Geolith Libretro

La documentación actual oficial de Libretro define explícitamente:

System Type (Restart) [geolith_system_type]

con valores para:

Neo Geo AES / Neo Geo MVS / Universe BIOS

y lo describe como el selector de System Type. La misma documentación exige BIOS y carga contenido .neo.

El upstream de Geolith confirma que el mismo core soporta AES y MVS, utiliza .NEO para cartridge systems y distingue recursos BIOS AES/MVS; además, un mismo juego puede comportarse en ambos modos y algunos requieren MVS. Por tanto, el contenido .neo por sí solo no constituye un selector AES/MVS documentado.

No encontré en la documentación/upstream actual una alternativa documentada para la ruta Libretro basada en:

tipo distinto de contenido AES/MVS;
dos builds/cores separados;
cartridge metadata que fuerce el modo;
parámetro de lanzamiento Libretro independiente;
API externa específica de Geolith que sustituya geolith_system_type.

BIOS distintos existen, pero esto no demuestra que Oz pueda seleccionar el modo meramente entregando un BIOS diferente. El contrato documentado para elegir System Type sigue siendo el core option.

Consecuencia Geolith

EMULATOR CAPABILITY: AES + MVS = YES upstream.
OZ INTEGRATION CAPABILITY para AES/MVS determinista: NO actualmente probado.

E3 — FinalBurn Neo Libretro

El upstream actual de FBNeo distingue internamente AES y MVS. NeoSetSystemType() deriva el hardware AES/MVS a partir de la selección de BIOS y otros flags del sistema.

Pero eso no significa que Oz pueda elegir simplemente “un archivo BIOS” en el lanzamiento Libretro. En la interfaz Libretro, la selección de modo se expone como fbneo-neogeo-mode; un issue upstream de agosto de 2025 reproduce explícitamente fbneo-neogeo-mode = "MVS_JAP" y su necesidad de reinicio para aplicar el modo.

Además, la evidencia upstream sobre Universe BIOS demuestra por qué “BIOS seleccionado = modo determinado automáticamente” no es una regla suficiente: históricamente Universe BIOS podía quedar en hardware MVS y requirió distinción adicional AES/MVS.

La evidencia todavía más reciente de abril de 2026 continúa describiendo dos listas AES/MVS y selección de modo dentro de las opciones del core.

Consecuencia FBNeo

EMULATOR CAPABILITY: AES + MVS = YES upstream.
BIOS participa en el comportamiento interno: YES.
BIOS presence alone como contrato determinista Oz: NO demostrado.
Ruta Libretro documentada/controlable: core option.
OZ INTEGRATION CAPABILITY hoy: NO para switching per-launch automatizado probado.

El arcade-fbneo legado de Oz no cambia esta conclusión: por instrucción del Product Owner, es contexto/evidencia y no autoridad arquitectónica.

E4 — MAME upstream

MAME presenta una semántica diferente. Su código actual tiene clases/configuraciones diferenciadas para arcade Neo Geo y AES, en lugar de tratar la diferencia exclusivamente como un core option de un único frontend.

La documentación oficial de MAME permite seleccionar un system y después software/media desde la línea de comandos, y las software lists identifican media contra una máquina determinada.

Esto produce una propiedad arquitectónica importante:

MAME machine/system selection → no Neo-Geo core-option transport inherent

Es decir, MAME upstream puede representar AES/MVS en el contrato de lanzamiento, no necesariamente mediante estado interno mutable del frontend.

Eso es una ventaja de launch-mode contract. No es evidencia de que MAME sea BEST EMULATOR.

E5 — MAME Current Libretro

Libretro documenta dos formas de ejecutar software-list systems con MAME Current:

direct MAME logic o .cmd para pasar comandos al core. El core actual declara soporte para cmd|zip|7z.

Pero aparece un problema específico de Android: existe un issue abierto del 3 de julio de 2026 en RetroArch que reporta que el launch de non-arcade software con MAME Current mediante .cmd, aun siguiendo la documentación, no funciona actualmente en Android.

Un issue no demuestra universalmente que todos los casos Android fallen. Sí significa que no podemos elevar .cmd a contrato Oz probado sin validación física propia.

E6 — mamearcade Libretro

El core-info actual describe mamearcade como la variante que incluye sólo la parte arcade de MAME, específicamente porque algunas plataformas, incluido Android, no pueden manejar el bundle completo. Admite cmd|zip|7z.

Por tanto:

MVS, como hardware arcade, es coherente con el scope del core.

AES, como máquina doméstica, no tiene una ruta establecida por esta evidencia en mamearcade.

No extrapolo que pueda lanzar AES sólo porque el upstream MAME completo puede hacerlo.

E7 — MAME4droid Current

MAME4droid Current es una ruta external-app Android activa basada en MAME moderno. Su upstream exige una configuración inicial de storage/ROM path mediante Scoped Storage, lo que ya impide clasificarlo automáticamente como ZERO_ADMIN_FIT: GOOD sin trabajo adicional.

Hay evidencia material de launcher interoperability: un issue del propio repositorio muestra una invocación externa mediante ACTION_VIEW, DATA y cli_params que pasa opciones de línea de comandos a MAME4droid.

Además, la configuración Android actual de ES-DE utiliza sistemáticamente ese contrato:

ACTION_VIEW + EXTRA_cli_params + DATA=<MAME system>

para varios sistemas MAME.

Esto es evidencia suficientemente material para mantener MAME4droid como candidato de integración a investigar.

Pero todavía no demuestra:

Oz → MAME4droid → AES/MVS físicamente.

No tenemos verificados para Oz en esta cualificación el contrato exacto de package/activity/intent, provisioning, SAF, lifecycle ni un lanzamiento Neo Geo AES/MVS real.

CANDIDATE MATRIX
Candidate	Emulator support	AES/MVS control exacto	AUTOMATABLE_WITH_CURRENT_OZ	DYNAMIC CORE OPTION	GLOBAL MUTATION	MANUAL UI TODAY	ZERO-ADMIN FIT
Geolith Libretro	AES + MVS upstream	geolith_system_type core option	NO para switching determinista probado	YES	NO inherentemente; un workaround compartido sería no aprobado/no probado	YES para cambiar modo con contratos actuales disponibles	POOR hoy / CONDITIONAL futuro
FinalBurn Neo Libretro	AES + MVS upstream	Neo-Geo mode/core option, que a su vez selecciona BIOS/system behavior	NO	YES	NO inherentemente; mismo problema de workaround compartido	YES para switching actual	POOR hoy / CONDITIONAL futuro
MAME Current Libretro	MAME upstream soporta ambas máquinas	system/software launch; .cmd en Libretro	NO, no probado en Android Oz	NO para AES/MVS selector	NO	NO por diseño, pero ruta automatizada Android no está probada	UNKNOWN / CONDITIONAL
MAME/Arcade Libretro	MVS compatible con scope arcade; AES no establecido aquí	content/arcade driver para MVS	NO para piloto AES+MVS combinado	NO para MVS	NO	NO para MVS	CONDITIONAL
MAME4droid Current	MAME-derived; máquina seleccionable mediante launch semantics	Android external launch + MAME system/CLI semantics	UNKNOWN — existe contrato externo material, no contrato Oz validado	NO	NO según mecanismo observado	UNKNOWN tras provisioning inicial	CONDITIONAL
Nota sobre REQUIRES_GLOBAL_MUTATION

Para Geolith/FBNeo marco NO como propiedad del emulador: sus core options no obligan conceptualmente a ser globales.

Pero, dada la evidencia A1/A2, Oz no dispone de la vía aislada per-launch que necesitamos. Mutar el .opt global/shared para recuperar automatización sería otra arquitectura/transport distinto y no debe asumirse autorizado ni probado.

EXACT MODE CONTROL
Candidate	AES_MODE_CONTROL	MVS_MODE_CONTROL
Geolith Libretro	geolith_system_type = AES	geolith_system_type = MVS
FBNeo Libretro	Neo-Geo mode selection → AES BIOS/system behavior	Neo-Geo mode selection → MVS BIOS/system behavior
MAME Current	Selección de máquina/system AES + software/media	Selección de máquina/system Neo Geo arcade + software/media
mamearcade Libretro	UNKNOWN / not established by arcade-only contract	Arcade driver/content path
MAME4droid Current	MAME system-selector launch is technically plausible; exact Oz AES invocation NOT VALIDATED	Same for MVS; exact Oz invocation NOT VALIDATED

Geolith source:
FBNeo source:
MAME/MAME Libretro:
MAME4droid external contract evidence:

EMULATOR CAPABILITY ≠ OZ INTEGRATION CAPABILITY

Este es el hallazgo central.

Geolith

AES/MVS emulator support = YES

pero:

Oz deterministic per-launch AES/MVS control = NO

porque la interfaz que necesitamos controlar es actualmente un core option y el transporte Oz para hacerlo quedó sin demostrar físicamente.

FBNeo

AES/MVS emulator support = YES

pero:

Oz deterministic per-launch AES/MVS control = NO

por el mismo límite de integración, aunque internamente FBNeo vincule hardware y BIOS.

MAME

AES/MVS deterministic system selection in emulator architecture = YES

pero:

Oz Android production launch contract = UNKNOWN / NOT VERIFIED

Por ello MAME mejora la arquitectura del selector, pero todavía no gana la integración.

Esto es exactamente coherente con la regla Oz de mantener separados configured, provisioned, physical launch, compatibility y certificación.

INFERENCE

I1. La cualificación anterior de Geolith como candidato físico prioritario debe corregirse en un aspecto: sigue siendo perfectamente válido para estudiar calidad de emulación, pero ya no puede asumirse integrable para un piloto AES+MVS automático bajo el contrato actual de Oz.

I2. FBNeo no resuelve el nuevo blocker sólo porque pueda seleccionar BIOS AES/MVS internamente. En la ruta Libretro la selección determinista continúa pasando por configuración del core.

I3. MAME es actualmente el único candidato investigado que demuestra un modelo de selección AES/MVS conceptualmente independiente del core-option transport, porque la máquina forma parte del launch identity.

I4. Ese hecho no justifica migrar el piloto a MAME. Sólo justifica estudiar un contrato Android acotado.

I5. mamearcade no es sustituto evidente: resolver MVS y perder/no demostrar AES no satisface el objetivo del piloto conjunto.

I6. Un harness manual puede seguir generando evidencia válida de compatibilidad/rendimiento de Geolith y FBNeo si los resultados etiquetan explícitamente:

manual mode setup ≠ Oz automatic launch evidence

UNKNOWN

Siguen abiertos y no deben convertirse en hechos:

Unknown	Estado
¿Existe otro transporte RetroArch actual que Oz pueda controlar de forma aislada?	UNKNOWN; A1/A2 cerrados, no “imposible para siempre”
¿Geolith ofrece fuera de su core option una API Libretro documentada para AES/MVS?	No encontrada; UNKNOWN salvo nueva evidencia
¿FBNeo puede seleccionarse per-launch sólo mediante contenido/BIOS externo sin core option?	No demostrado
¿MAME Current .cmd funciona en el dispositivo Oz/RA actual para AES?	NOT RUN
¿mamearcade incluye una ruta AES utilizable en Android?	NOT ESTABLISHED
¿MAME4droid acepta exactamente el contrato requerido para Neo Geo AES/MVS desde Oz?	NOT RUN
package / Activity / flags/Intent exactos que Oz debería usar para MAME4droid	UNKNOWN — no inventados
¿Puede provisionarse MAME4droid zero-admin sin setup manual SAF?	UNKNOWN
compatibilidad comparada Geolith vs FBNeo vs MAME	NOT RUN
rendimiento/latencia comparados	NOT RUN
save-state quality comparada	NOT RUN
BEST EMULATOR	OPEN
PRODUCT-FIT CONSEQUENCE

Geolith Libretro: excelente candidato potencial desde la perspectiva de especialización Neo Geo, pero launch-mode product fit actualmente bloqueado para un catálogo que requiera AES y MVS determinísticos sin intervención.

FBNeo Libretro: misma clase de bloqueo de integración. Que ya exista experiencia previa con FBNeo en Oz no cambia la evaluación.

MAME Current Libretro: contrato conceptualmente más expresivo, pero la ruta Android/Libretro no alcanza todavía el estándar de evidencia Oz, y el issue Android de 2026 hace especialmente imprudente declararla resuelta.

MAME4droid Current: la alternativa más interesante para una sub-cualificación de launch contract, porque evita conceptualmente el core-option blocker y existe evidencia real de launcher externo Android. Sin embargo, no está físicamente verificada con Oz, y su configuración inicial de storage introduce una condición zero-admin adicional.

Esto no equivale a recomendar MAME4droid como emulador final.

RECOMMENDATION
B — Continue qualification with separate bounded physical runs; Oz integration BLOCKED

Recomiendo:

Geolith AES manual run
Geolith MVS manual run
FBNeo AES manual run
FBNeo MVS manual run

como harness de cualificación exclusivamente, para no perder la capacidad de comparar:

accuracy / compatibility / performance / latency / saves / input / BIOS behavior

mientras se mantiene explícitamente:

OZ AUTOMATIC AES/MVS INTEGRATION: BLOCKED

En paralelo, Forge puede evaluar si merece autorizar un sub-scope R2 muy estrecho para demostrar o refutar físicamente:

Oz → Android external launch → MAME4droid Current → explicit AES/MVS system selection

sin convertirlo en un proyecto general de integración MAME.

No recomiendo A, porque hoy no hay mecanismo productivo demostrado.

No recomiendo todavía C, porque MAME/MAME4droid presenta un contrato potencialmente superior pero no uno ya verificado por Oz.

No recomiendo D, porque el bloqueo de integración no impide seguir acumulando evidencia de emulación mediante un harness no productivo. Suspender toda la cualificación perdería información necesaria para BEST EMULATOR WINS.

RISK / FORGE ESCALATION

La investigación upstream y las ejecuciones manuales comparativas son R0/R1 dentro de Platform Lab.

La pregunta “¿debe Platform Factory añadir un launch-mode contract genérico, adoptar una ruta external-app MAME, o cambiar el contrato de integración para Neo Geo?” es R2 porque afecta EmulatorProfile/launch routing/provisioning y potencialmente a plataformas futuras. Esa decisión corresponde a Forge.

Nada aquí justifica R3. Si posteriormente se pretendiera declarar soporte público/OZ-EQS, eso sí entra en la gobernanza crítica correspondiente.

HANDOFF STATE

EVIDENCE: Geolith/FBNeo soportan AES/MVS pero las rutas Libretro verificadas dependen de selección de core option; Oz no tiene actualmente transporte dinámico aislado probado. MAME upstream dispone de un selector de máquina distinto del core-option model; MAME4droid presenta evidencia material de external launch, pero no evidencia Oz física.

INFERENCE: MAME merece una prueba acotada de contrato de integración; no merece todavía sustituir los candidatos de emulación.

UNKNOWN: contrato físico Oz→MAME4droid, zero-admin provisioning y calidad comparativa de emulación.

RECOMMENDATION: B.

NEXT OWNER: 🔥 Oz Forge IV — Command

IMPLEMENTATION_READY: NO

STOP_TO_FORGE: YES