# Bluetti Bridge Raspberry

Bridge para operar Bluetti via Raspberry Pi, expondo API REST, WebSocket, MQTT, Dashboard Web, automações locais e integração com Node-RED/Home Assistant.

## Objetivo

Fluxo recomendado:

```text
Bluetti -> Bluetooth BLE -> Raspberry Pi -> API/MQTT/WebSocket -> App Android / Node-RED / Home Assistant / Internet
```

Assim o celular não precisa ficar perto da bateria. O Raspberry mantém a conexão Bluetooth 24h e o app controla pela rede.

## Recursos

- Conexão BLE com Bluetti
- Fallback de canais BLE para modelos novos, incluindo AC180P
- Reconexão automática
- API REST
- WebSocket tempo real
- MQTT
- Dashboard Web
- Automações de bateria baixa/alta
- Controle AC ON/OFF
- Controle DC ON/OFF
- Multi-dispositivo via configuração
- Pronto para CasaOS, Docker e Portainer

## Instalação rápida no Raspberry/CasaOS

```bash
cd /DATA/AppData
mkdir -p bluetti-bridge
cd bluetti-bridge
```

Copie esta pasta `raspberry-bridge` para o Raspberry e rode:

```bash
sudo docker compose up -d --build
```

Acesse:

```text
http://IP_DO_RASPBERRY:5055
```

Exemplo:

```text
http://192.168.1.72:5055
```

## Configuração

Edite `config.yaml`:

```yaml
bridge:
  host: "0.0.0.0"
  port: 5055

devices:
  - id: "ac180p"
    name: "Bluetti AC180P"
    mac: "C7:A3:2B:21:77:90"
    model: "AC180P"
    enabled: true
```

## API REST

### Status geral

```http
GET /api/status
```

### Status de um dispositivo

```http
GET /api/devices/ac180p/status
```

### Comandos

```http
POST /api/devices/ac180p/ac/on
POST /api/devices/ac180p/ac/off
POST /api/devices/ac180p/dc/on
POST /api/devices/ac180p/dc/off
POST /api/devices/ac180p/read
```

### Automações

```http
GET /api/automation
POST /api/automation
```

Payload:

```json
{
  "enabled": true,
  "ac_off_below": 20,
  "ac_on_above": 80,
  "dc_off_below": 15,
  "dc_on_above": 75
}
```

## MQTT

Tópicos publicados:

```text
bluetti/ac180p/status
bluetti/ac180p/battery
bluetti/ac180p/input_total
bluetti/ac180p/output_total
bluetti/ac180p/ac
bluetti/ac180p/dc
bluetti/ac180p/online
```

Comandos MQTT:

```text
bluetti/ac180p/cmd
```

Payloads:

```json
{"command":"ac_on"}
{"command":"ac_off"}
{"command":"dc_on"}
{"command":"dc_off"}
{"command":"read"}
```

## Home Assistant REST exemplo

```yaml
sensor:
  - platform: rest
    name: Bluetti AC180P
    resource: http://192.168.1.72:5055/api/devices/ac180p/status
    value_template: "{{ value_json.battery }}"
    unit_of_measurement: "%"
```

## Node-RED

Use HTTP Request para:

```text
GET http://192.168.1.72:5055/api/devices/ac180p/status
```

Ou MQTT-in nos tópicos `bluetti/ac180p/#`.
