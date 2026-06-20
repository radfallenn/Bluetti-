# node-red-contrib-bluetti-direct

Nó Node-RED para controle direto de equipamentos Bluetti via Bluetooth BLE.

## Recursos

- Conexão direta via Bluetooth BLE pelo MAC.
- Leitura periódica de:
  - bateria `%`
  - entrada AC W
  - entrada DC W
  - saída AC W
  - saída DC W
  - estado AC ligado/desligado
  - estado DC ligado/desligado
- Comandos:
  - `ac_on`
  - `ac_off`
  - `dc_on`
  - `dc_off`
  - `read`
  - `connect`

## Instalação local no Node-RED / CasaOS

Copie a pasta `node-red-contrib-bluetti-direct` para o servidor e rode:

```bash
cd ~/.node-red
npm install /caminho/para/node-red-contrib-bluetti-direct
node-red-restart
```

Em Docker/CasaOS, entre no container ou monte a pasta no volume do Node-RED e instale pelo terminal do container.

## Entrada do nó

Envie no `msg.payload`:

```json
{"command":"read"}
```

```json
{"command":"ac_on"}
```

```json
{"command":"ac_off"}
```

```json
{"command":"dc_on"}
```

```json
{"command":"dc_off"}
```

## Saída 1

```json
{
  "battery": 80,
  "acInputW": 0,
  "dcInputW": 120,
  "acOutputW": 300,
  "dcOutputW": 20,
  "acEnabled": true,
  "dcEnabled": true,
  "online": true,
  "updatedAt": "2026-06-20T10:00:00.000Z"
}
```

## Saída 2

Eventos de erro e diagnóstico.

## Observação

Modelos Bluetti com firmware novo, protocolo criptografado ou características BLE diferentes podem conectar e ainda assim não responder aos registradores padrão. Nesse caso será necessário mapear os UUIDs/registradores reais pelo log.
