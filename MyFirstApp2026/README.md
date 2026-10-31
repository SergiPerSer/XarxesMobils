# README --- Aplicación Android de control de robot por Bluetooth

## 1. Descripción general

Este proyecto contiene una actividad Android escrita en Java
(`MainActivity`) que permite buscar dispositivos Bluetooth cercanos,
establecer una conexión Bluetooth clásico mediante un socket RFCOMM y
enviar órdenes de movimiento y velocidad desde una interfaz gráfica.

La aplicación implementa la parte Android de la comunicación. El código
envía comandos de texto terminados en salto de línea (`\n`); por tanto,
el dispositivo receptor debe estar preparado para aceptar una conexión
Bluetooth clásico compatible con SPP y entender esos comandos.

> **Importante:** el código no define el firmware del robot ni la
> interpretación física de las órdenes. Los significados de `w`, `s`,
> `a`, `d`, `q`, `e`, `0` y los números deben coincidir con el programa
> que recibe los datos.

## 2. Funcionalidades principales

-   Comprobar si el dispositivo Android dispone de Bluetooth y si está
    activado.
-   Solicitar permisos de Bluetooth y ubicación cuando corresponda a la
    versión de Android.
-   Buscar dispositivos Bluetooth cercanos y mostrarlos en la interfaz.
-   Intentar conectar con el dispositivo seleccionado mediante
    RFCOMM/SPP.
-   Enviar órdenes para avanzar, retroceder, girar, desplazarse
    lateralmente y detenerse.
-   Enviar el valor de velocidad indicado por una barra (`SeekBar`).
-   Mostrar mensajes de estado con colores para comunicar el resultado
    de las operaciones.
-   Ejecutar la conexión y las escrituras Bluetooth en un hilo de
    trabajo para evitar bloquear la interfaz.
-   Cerrar el socket y liberar recursos al desconectar o destruir la
    actividad.

## 3. Tecnologías y clases utilizadas

  -----------------------------------------------------------------------
  Clase o API                         Función en el proyecto
  ----------------------------------- -----------------------------------
  `AppCompatActivity`                 Base de la pantalla principal
                                      Android.

  `BluetoothAdapter`                  Consulta el estado de Bluetooth e
                                      inicia la búsqueda de dispositivos.

  `BluetoothDevice`                   Representa cada dispositivo
                                      detectado.

  `BluetoothSocket`                   Mantiene la conexión RFCOMM con el
                                      dispositivo remoto.

  `InputStream`                       Se obtiene del socket, aunque en
                                      este código no se leen datos
                                      entrantes.

  `OutputStream`                      Envía comandos al dispositivo
                                      remoto.

  `BroadcastReceiver`                 Recibe los eventos del sistema
                                      relacionados con la búsqueda
                                      Bluetooth.

  `ActivityResultLauncher`            Gestiona solicitudes de permisos y
                                      la activación de Bluetooth.

  `ExecutorService`                   Ejecuta operaciones potencialmente
                                      bloqueantes fuera del hilo
                                      principal.

  `SeekBar`                           Permite seleccionar la velocidad.

  `TextView`, `Button`,               Elementos visuales de la interfaz.
  `LinearLayout`                      

  `ContextCompat`                     Comprueba permisos y registra el
                                      receptor de eventos con
                                      compatibilidad AndroidX.

  `EdgeToEdge`, `WindowInsetsCompat`  Ajustan el contenido a las barras
                                      del sistema.
  -----------------------------------------------------------------------

## 4. Estructura de la clase

La clase principal es:

``` java
public class MainActivity extends AppCompatActivity
```

`MainActivity` reúne la inicialización de la interfaz, la gestión de
permisos, el descubrimiento Bluetooth, la conexión y el envío de
órdenes.

### 4.1. Estado y elementos de la interfaz

-   `bluetoothActive`: variable estática que indica si el método
    `checkBluetooth()` ha confirmado que Bluetooth está activado.
-   `velocity`: variable entera declarada para la velocidad. En el
    código aportado no se actualiza ni se utiliza para enviar órdenes;
    el valor efectivo se pasa directamente desde la barra al método
    `setRobotVelocity()`.
-   `conButton`, `desButton`: referencias a los botones de conexión y
    desconexión. El botón de conexión parece invocar
    `onClickConnectButton` desde XML, ya que no se le asigna un
    `OnClickListener` en `onCreate()`.
-   `forButton`, `backButton`: avance y retroceso.
-   `leftButton`, `rightButton`: desplazamiento lateral.
-   `turnleftButton`, `turnrightButton`: giros sobre el eje.
-   `stopButton`: orden de parada.
-   `robotStatus`: texto de estado de Bluetooth/conexión.
-   `velBar`, `velValue`: barra de velocidad y etiqueta que muestra su
    valor.
-   `discoveredDevices`: contenedor donde se insertan dinámicamente los
    dispositivos encontrados.

Estas referencias se enlazan con los componentes del XML mediante
`findViewById(R.id...)`. Los identificadores deben existir en
`res/layout/activity_main.xml`.

### 4.2. Recursos de comunicación

-   `bluetooth`: adaptador Bluetooth del teléfono.
-   `bluetoothSocket`: socket RFCOMM de la conexión activa.
-   `inputStream`: flujo de entrada del socket. Se obtiene, pero no se
    utiliza para recibir datos.
-   `outputStream`: flujo de salida usado para enviar comandos.
-   `connectionExecutor`: ejecutor de un solo hilo, creado con
    `Executors.newSingleThreadExecutor()`. Serializa las tareas que se
    envían a ese ejecutor.
-   `deviceList`: lista de dispositivos descubiertos, utilizada para
    evitar mostrar el mismo dispositivo más de una vez.
-   `receiverRegistered`: indica si el receptor de eventos ya se ha
    registrado.
-   Los cuatro `ActivityResultLauncher` gestionan la activación de
    Bluetooth y las solicitudes de permisos.

### 4.3. Constantes del protocolo de comandos

  -------------------------------------------------------------------------
  Constante                 Texto enviado           Uso que muestra la
                                                    aplicación
  ------------------------- ----------------------- -----------------------
  `COMMAND_FORWARD`         `w`                     Avanzar

  `COMMAND_BACKWARD`        `s`                     Retroceder

  `COMMAND_TURN_LEFT`       `a`                     Girar a la izquierda

  `COMMAND_TURN_RIGHT`      `d`                     Girar a la derecha

  `COMMAND_LATERAL_LEFT`    `q`                     Desplazarse
                                                    lateralmente a la
                                                    izquierda

  `COMMAND_LATERAL_RIGHT`   `e`                     Desplazarse
                                                    lateralmente a la
                                                    derecha

  `COMMAND_STOP`            `0`                     Detenerse
  -------------------------------------------------------------------------

Cada comando se transmite como texto UTF-8 seguido de `\n`. La
aplicación no envía directamente velocidades angulares, distancias ni
consignas de motores: envía los caracteres definidos por el protocolo.

## 5. Ciclo de vida e inicialización: `onCreate()`

`onCreate()` prepara la actividad al abrirse.

1.  Llama a `super.onCreate(savedInstanceState)`.
2.  Activa el modo Edge-to-Edge.
3.  Carga la interfaz con `setContentView(R.layout.activity_main)`.
4.  Recupera los controles con `findViewById`.
5.  Actualiza la etiqueta de velocidad a partir del valor inicial de la
    barra.
6.  Registra el listener de `velBar`.
7.  Asocia los botones de movimiento, parada y desconexión con sus
    métodos.
8.  Registra los lanzadores para activar Bluetooth y solicitar permisos.
9.  Instala el listener de insets para respetar las barras del sistema.

### Barra de velocidad

El `OnSeekBarChangeListener` contiene tres callbacks:

-   `onProgressChanged`: actualiza la etiqueta y llama a
    `setRobotVelocity(progress)` cada vez que cambia el valor.
-   `onStartTrackingTouch`: no realiza ninguna operación.
-   `onStopTrackingTouch`: tampoco realiza ninguna operación.

**Consecuencia:** el cambio de velocidad se envía mientras se mueve la
barra, no solamente al soltarla. Esto puede generar múltiples mensajes
consecutivos. El receptor debe poder procesarlos adecuadamente.

### Botones de movimiento

Los listeners conectan cada botón con un método específico. Por ejemplo,
pulsar el botón de avance llama a `moveForward()`, que termina enviando
el comando `w`.

El botón de conexión no aparece enlazado en este bloque mediante
`setOnClickListener`; hay que verificar en `activity_main.xml` si tiene
`android:onClick="onClickConnectButton"`.

## 6. Conexión y descubrimiento Bluetooth

### 6.1. `onClickConnectButton(View view)`

Es el punto de entrada del proceso de conexión cuando se pulsa el botón
correspondiente.

El método sigue este orden:

1.  En Android 12 o superior (`SDK_INT >= S`), comprueba el permiso
    `BLUETOOTH_CONNECT`.
2.  Si falta el permiso, solicita autorización y termina el método.
3.  Comprueba si `bluetooth` es `null`. Si lo es, llama a
    `checkBluetooth()` y termina.
4.  Si Bluetooth está desactivado, solicita su activación mediante
    `requestEnableBluetooth()`.
5.  Si está disponible y activado, llama a `checkBluetooth()` y
    `startDiscovery()`.

La búsqueda se inicia únicamente después de superar esas comprobaciones.

### 6.2. `requestEnableBluetooth()`

Actualiza el estado para indicar que se está solicitando la activación y
lanza un `Intent` con `BluetoothAdapter.ACTION_REQUEST_ENABLE`.

Cuando el usuario responde al diálogo, se ejecuta el callback de
`enableBluetoothLauncher`. Si Bluetooth está activado, vuelve a
comprobar su estado e inicia el descubrimiento; si no, muestra el
mensaje correspondiente.

### 6.3. `checkBluetooth()`

Comprueba la disponibilidad y el estado del adaptador:

-   Si el adaptador es `null`, muestra que el dispositivo no tiene
    Bluetooth.
-   Si está desactivado, muestra el aviso y establece
    `bluetoothActive = false`.
-   Si está activado, muestra su nombre y establece
    `bluetoothActive = true`.
-   Si se produce una `SecurityException`, informa de que falta el
    permiso.

Esta variable describe el estado del adaptador según la última
comprobación; **no significa que exista una conexión con el robot**.

### 6.4. Gestión de permisos

El código separa las comprobaciones y solicitudes de permisos en varios
métodos:

-   `requestDiscoveryPermission()`: solicita `BLUETOOTH_SCAN` en Android
    12 o superior.
-   `hasDiscoveryPermission()`: comprueba `BLUETOOTH_SCAN` en Android 12
    o superior; en versiones anteriores devuelve `true`.
-   `requiresLocationPermissionForDiscovery()`: devuelve `true` desde
    Android Marshmallow (API 23).
-   `hasLocationPermission()`: comprueba si se ha concedido ubicación
    precisa o aproximada.
-   `requestLocationPermission()`: solicita ambos permisos de ubicación.

Los resultados se reciben en los callbacks de
`discoveryPermissionLauncher` y `locationPermissionLauncher`. Si el
permiso necesario se concede, se vuelve a intentar la búsqueda.

**Detalle importante:** en Android 12 o superior, la búsqueda requiere
normalmente `BLUETOOTH_SCAN`; la conexión requiere `BLUETOOTH_CONNECT`.
La necesidad de permisos de ubicación depende de la versión de Android y
de cómo se declare y configure la aplicación. El código solicita
ubicación desde API 23, por lo que conviene comprobar que esa estrategia
corresponde a las versiones objetivo y a los permisos declarados en el
manifiesto.

### 6.5. `startDiscovery()`

Inicia la búsqueda de dispositivos cercanos.

1.  Comprueba el permiso de ubicación; si falta, lo solicita y termina.
2.  Comprueba el permiso de búsqueda; si falta, lo solicita y termina.
3.  Limpia `deviceList` y elimina las vistas anteriores del contenedor.
4.  Muestra el mensaje «Buscando dispositivos...».
5.  Prepara un `IntentFilter` para tres eventos:
    -   `BluetoothDevice.ACTION_FOUND`
    -   `BluetoothAdapter.ACTION_DISCOVERY_STARTED`
    -   `BluetoothAdapter.ACTION_DISCOVERY_FINISHED`
6.  Registra `discoveryResult` si todavía no estaba registrado.
7.  Si ya hay una búsqueda en curso, lo deja registrado en el log y
    termina.
8.  En caso contrario, llama a `bluetooth.startDiscovery()` y registra
    el resultado.

Si `startDiscovery()` devuelve `false`, muestra un aviso. El método no
garantiza por sí solo que se encuentre un dispositivo: el resultado
depende del entorno, permisos, estado de Bluetooth y dispositivos
cercanos.

### 6.6. `discoveryResult`: receptor de eventos

El `BroadcastReceiver` procesa los eventos de búsqueda:

-   **Búsqueda iniciada:** registra «Búsqueda iniciada».
-   **Búsqueda terminada:** registra «Búsqueda terminada» y, si la lista
    está vacía, muestra «No se encontraron dispositivos».
-   **Dispositivo encontrado:** extrae el dispositivo, su nombre y la
    intensidad de señal RSSI; comprueba si su dirección ya está en
    `deviceList`; si es nuevo, lo añade y crea un botón para
    seleccionarlo. También registra nombre y RSSI.

El RSSI se registra en dBm, pero no se utiliza para ordenar ni filtrar
dispositivos.

### 6.7. `addDeviceMessage(String message)`

Crea un `TextView`, configura texto, tamaño y márgenes internos, y lo
añade a `discoveredDevices`. Sirve para mostrar mensajes como el inicio
de la búsqueda o la ausencia de dispositivos.

### 6.8. `addDiscoveredDevice(BluetoothDevice device, String name)`

Crea un botón dinámico por dispositivo encontrado. Muestra el nombre, la
dirección Bluetooth y una indicación para tocarlo. Al pulsarlo, llama a
`connectBD(device, name, row)`.

## 7. Establecimiento de la conexión: `connectBD(...)`

Este método intenta crear una conexión RFCOMM con el dispositivo
elegido.

1.  Comprueba `BLUETOOTH_CONNECT` en Android 12 o superior.
2.  Desactiva temporalmente el botón seleccionado y muestra
    «Conectando...».
3.  Envía la operación de conexión a `connectionExecutor`.
4.  Si hay una búsqueda activa, la cancela.
5.  Crea un socket con `createRfcommSocketToServiceRecord(...)` usando
    el UUID `00001101-0000-1000-8000-00805F9B34FB`, asociado
    habitualmente al servicio serie Bluetooth clásico SPP.
6.  Llama a `connect()`.
7.  Obtiene los flujos de entrada y salida del socket.
8.  Vuelve al hilo de interfaz mediante `runOnUiThread()`, cierra la
    conexión anterior y guarda el nuevo socket y sus flujos.
9.  Actualiza el botón y el estado visual para indicar que la conexión
    se ha establecido.

### Tratamiento de errores

Si ocurre una excepción durante el intento:

-   Registra el error en Logcat.
-   Intenta cerrar el socket que ha fallado.
-   Reactiva el botón para permitir un nuevo intento.
-   Muestra un mensaje de conexión fallida y una sugerencia para
    comprobar el emparejamiento y la compatibilidad SPP.

La conexión solo se considera establecida cuando `connect()` termina
correctamente. La detección de un dispositivo durante la búsqueda no
implica que esté conectado ni que acepte el protocolo esperado.

## 8. Envío de órdenes al robot

### 8.1. Métodos de movimiento

Los métodos `moveForward()`, `moveBackward()`, `turnLeft()`,
`turnRight()`, `moveLaterallyLeft()`, `moveLaterallyRight()` y
`stopRobot()` son envoltorios sencillos. Cada uno llama a
`sendRobotCommand()` con un comando y una descripción para la interfaz.

Esta separación facilita la lectura: los botones expresan la intención
de movimiento, mientras que el método común centraliza la comunicación.

### 8.2. `setRobotVelocity(int velocity)`

Convierte el entero recibido en una cadena mediante
`String.valueOf(velocity)` y la envía como comando.

Por ejemplo, si la barra tiene valor `35`, se envía el texto `35\n`. El
código Android no establece el rango válido ni las unidades de esa
velocidad: deben definirse en la interfaz y en el programa receptor.

### 8.3. `sendRobotCommand(String command, String description)`

Es el método central de transmisión.

1.  Comprueba que `bluetoothSocket` no sea `null`, que esté conectado y
    que `outputStream` exista.
2.  Si no se cumple alguna condición, muestra «No hay un robot
    conectado» y termina.
3.  Envía la escritura al ejecutor.
4.  Dentro de la tarea, captura el flujo de salida actual.
5.  Escribe `(command + "\n")` codificado como UTF-8.
6.  Llama a `flush()` para vaciar el flujo.
7.  Si la escritura termina, actualiza el estado con la descripción y un
    color verde.
8.  Si aparece `IOException` o `SecurityException`, registra el error y
    muestra que la conexión puede haberse perdido.

El salto de línea funciona como delimitador entre comandos, siempre que
el receptor lea y separe los mensajes con el mismo criterio.

**Límite de confirmación:** el mensaje verde indica que la escritura
local no lanzó una excepción. No confirma que el robot haya recibido,
interpretado o ejecutado físicamente la orden; el código no implementa
una respuesta de confirmación desde el dispositivo.

## 9. Desconexión y liberación de recursos

### 9.1. `disconnectFromRobot()`

Si no existe socket, muestra que no hay robot conectado. Si existe:

1.  Muestra «Desconectando...».
2.  Programa la desconexión en `connectionExecutor`.
3.  Intenta enviar primero el comando de parada `0\n`, si el socket
    sigue conectado.
4.  Intenta cerrar el socket.
5.  En el hilo de interfaz, comprueba que el socket global siga siendo
    el que se está cerrando.
6.  Si coincide, pone `bluetoothSocket`, `inputStream` y `outputStream`
    a `null`.
7.  Muestra el estado «Desconectado».

El envío de la orden de parada es un intento: puede fallar y el error se
registra en el log. Por seguridad, no debe suponerse que la orden llegó
al robot.

### 9.2. `closeCurrentSocket()`

Cierra el socket actual, si existe, y limpia las referencias al socket y
a sus flujos. Se utiliza antes de guardar una nueva conexión y durante
la destrucción de la actividad.

### 9.3. `onDestroy()`

Antes de finalizar la actividad:

-   Cierra el socket actual.
-   Llama a `shutdownNow()` sobre `connectionExecutor`.
-   Si el receptor se había registrado, lo desregistra y actualiza
    `receiverRegistered`.
-   Llama a `super.onDestroy()`.

Esto reduce el riesgo de mantener conexiones y receptores registrados
cuando la pantalla deja de existir. No sustituye a una estrategia de
reconexión o a un servicio de larga duración si el control debe
continuar con la aplicación en segundo plano.

## 10. Actualización visual del estado

### `updateVelocityLabel(int value)`

Actualiza el texto de `velValue` con el formato «Velocidad: valor».

### `showConnectionStatus(String message, int color)`

Actualiza el texto de `robotStatus` y su color. El código utiliza, por
ejemplo:

-   Verde (`RGB 46, 125, 50`): conexión o envío local satisfactorio.
-   Rojo (`RGB 183, 28, 28`): error o ausencia de conexión.
-   Naranja (`RGB 245, 124, 0`): operación en curso.
-   Gris (`RGB 117, 117, 117`): estado desconectado.

Estos colores son una convención visual; el texto es el que describe la
situación concreta.

## 11. Flujo completo de ejecución

``` text
Inicio de MainActivity
        |
        v
Cargar interfaz y asociar listeners
        |
        v
Pulsar Conectar
        |
        v
Comprobar permisos y estado de Bluetooth
        |
        +---- Falta permiso ---> Solicitar permiso
        |
        +---- Bluetooth apagado ---> Solicitar activación
        |
        v
startDiscovery()
        |
        v
BroadcastReceiver recibe ACTION_FOUND
        |
        v
Mostrar dispositivos como botones
        |
        v
Seleccionar un dispositivo
        |
        v
connectBD() -> crear socket RFCOMM -> connect()
        |
        +---- Error ---> Mostrar fallo y permitir reintento
        |
        v
Guardar socket y OutputStream
        |
        v
Pulsar movimiento / parada o cambiar velocidad
        |
        v
sendRobotCommand() -> escribir comando + "\n"
        |
        v
El dispositivo receptor interpreta el comando
        |
        v
Desconectar -> intentar enviar "0" -> cerrar socket
```

## 12. Qué ocurre en un ejemplo práctico

Supongamos que el usuario pulsa el botón de avance:

1.  El listener de `forButton` invoca `moveForward()`.
2.  `moveForward()` llama a
    `sendRobotCommand(COMMAND_FORWARD, "Avanzando")`.
3.  `COMMAND_FORWARD` contiene `w`.
4.  El método verifica que el socket y el flujo de salida sean válidos.
5.  La tarea de escritura envía los bytes correspondientes a `w\n`.
6.  Si no se produce una excepción de escritura, la interfaz muestra
    «Avanzando».

La aplicación no calcula una trayectoria ni controla directamente
motores desde este método. El movimiento real depende de la lógica que
reciba y procese el carácter `w`.

## 13. Aspectos que conviene revisar o mejorar

Esta sección distingue observaciones directas del código de posibles
mejoras de diseño.

### 13.1. Lectura de datos de retorno

`inputStream` se obtiene, pero nunca se consume. Por tanto, la
aplicación no recibe telemetría, confirmaciones ni errores enviados por
el dispositivo. Si el protocolo contempla respuestas, hará falta
implementar un lector en segundo plano y definir cómo se muestran esos
datos.

### 13.2. Permisos y manifiesto

Comprobar que `AndroidManifest.xml` declare los permisos apropiados para
las versiones objetivo de Android. Las comprobaciones en tiempo de
ejecución no sustituyen las declaraciones del manifiesto. Revisar
también el comportamiento de `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` y
ubicación en las versiones de Android que se quieran admitir.

### 13.3. Receptor de descubrimiento

`discoveryResult` modifica vistas de la interfaz desde `onReceive()`.
Los receptores registrados en el contexto de la aplicación suelen
ejecutarse en el hilo principal, pero conviene mantener explícita y
consistente la política de actualización de la UI. También es
recomendable revisar los permisos requeridos para obtener nombre,
dirección y RSSI.

### 13.4. Hilo de conexión y envío

El uso de un ejecutor de un solo hilo evita ejecutar la conexión y las
escrituras directamente en el hilo principal y serializa las tareas
enviadas a ese ejecutor. Sin embargo, hay que revisar los posibles
conflictos entre reconexión, desconexión y envío de órdenes,
especialmente si se pulsan botones rápidamente.

### 13.5. Estado de conexión

`bluetoothActive` solo refleja si el adaptador está activado, no si el
socket está conectado. Para representar el estado real de la sesión,
sería más claro mantener estados separados, por ejemplo: Bluetooth
desactivado, buscando, conectando, conectado, desconectando y error.

### 13.6. Velocidad

La variable `velocity` está declarada, pero no se usa en el código
mostrado. La velocidad que se transmite proviene directamente del
progreso de `velBar`. Deben definirse y validarse el mínimo, máximo y
unidades del valor, y comprobar que el receptor espera un número
independiente como comando.

### 13.7. Parada segura

El método de desconexión intenta enviar la orden `0`, pero una
interrupción o fallo de enlace puede impedir que llegue. Para un robot
físico, la parada segura no debe depender exclusivamente de una orden
enviada por Bluetooth: el firmware y el diseño del sistema deben
contemplar pérdida de comunicación y mecanismos de parada apropiados.

### 13.8. Limpieza y robustez

Se importan algunas clases que no se utilizan en el fragmento (`Color`
sí se utiliza; `View` también, pero ciertos imports aparecen
duplicados). Conviene eliminar imports repetidos o innecesarios. También
es recomendable comprobar errores de permisos en todas las llamadas
Bluetooth sensibles y manejar explícitamente los estados de búsqueda y
desconexión.

## 14. Guía de estudio por bloques

Para estudiar el programa sin intentar comprender todo a la vez, se
recomienda este orden:

1.  **Interfaz:** `onCreate()`, `findViewById()` y los listeners.
2.  **Comandos:** constantes `COMMAND_*`, métodos de movimiento y
    `setRobotVelocity()`.
3.  **Bluetooth básico:** `checkBluetooth()` y
    `requestEnableBluetooth()`.
4.  **Permisos:** `hasDiscoveryPermission()`, `hasLocationPermission()`
    y sus lanzadores.
5.  **Descubrimiento:** `startDiscovery()`, `discoveryResult`,
    `addDeviceMessage()` y `addDiscoveredDevice()`.
6.  **Conexión:** `connectBD()` y el uso de `BluetoothSocket`.
7.  **Comunicación:** `sendRobotCommand()` y `OutputStream`.
8.  **Desconexión y ciclo de vida:** `disconnectFromRobot()`,
    `closeCurrentSocket()` y `onDestroy()`.
9.  **Diagnóstico:** mensajes de `Log`, excepciones y actualización de
    `robotStatus`.

### Preguntas de autoevaluación

-   ¿Qué diferencia hay entre tener Bluetooth activado y tener un socket
    conectado?
-   ¿Por qué se usa `connectionExecutor` para conectar y escribir?
-   ¿Qué evento hace que aparezca un dispositivo en la lista?
-   ¿Qué UUID utiliza el código para crear el socket RFCOMM?
-   ¿Qué bytes se envían cuando el usuario pulsa avanzar?
-   ¿Qué papel cumple `\n` al final de cada orden?
-   ¿La etiqueta verde demuestra que el robot ejecutó físicamente la
    orden? ¿Por qué?
-   ¿Qué función tiene `inputStream` en el código actual y qué falta
    para usarlo?
-   ¿Qué intenta hacer `disconnectFromRobot()` antes de cerrar el
    socket?
-   ¿Qué podría ocurrir si se pierde Bluetooth mientras el robot se está
    moviendo?

## 15. Resumen final

`MainActivity` actúa como interfaz y cliente Bluetooth del sistema de
control. Inicializa la pantalla, solicita permisos, busca dispositivos,
permite seleccionar uno y abre un socket RFCOMM. Una vez conectado,
traduce las acciones de la interfaz a comandos de texto y los transmite
mediante `OutputStream`. Finalmente, intenta detener el robot y cerrar
la conexión al desconectar.

La idea central para entender el diseño es separar tres niveles:

1.  **Interfaz de usuario:** botones, barra de velocidad y mensajes.
2.  **Comunicación Bluetooth:** búsqueda, socket, permisos y flujos de
    datos.
3.  **Control del robot:** interpretación de los comandos por parte del
    dispositivo remoto, que no está implementada en este archivo.

Esta separación permite localizar errores: un problema visual pertenece
al primer nivel; un fallo de conexión o escritura, al segundo; y una
orden recibida pero ejecutada incorrectamente puede depender del
protocolo o del firmware del tercer nivel.

