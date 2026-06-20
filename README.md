# Bluetti Studio rAd

Repositório com duas partes:

- `integration/hassio-bluetti-bt-main`: integração Home Assistant Bluetti BT corrigida com melhorias de estabilidade, retries, cache e diagnóstico.
- `app/BluettiControlApp`: app Android para controlar e monitorar dispositivos Bluetti via Home Assistant API.

## Como gerar o APK

Entre na aba **Actions** do GitHub e rode o workflow **Build Bluetti APK**. O APK será gerado como artefato.

## Observação

O app usa a API do Home Assistant para evitar conflito de Bluetooth entre celular, Raspberry/Home Assistant e equipamentos Bluetti.
