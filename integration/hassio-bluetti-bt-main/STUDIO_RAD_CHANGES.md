# Correções e melhorias Studio rAd

Implementado nesta versão:

- `bluetti-bt-lib` unificado para `0.1.6`.
- `max_retries` agora é aplicado no polling quando a biblioteca instalada suporta o parâmetro.
- Polling com timeout duro ao redor da leitura BLE.
- Retry com backoff e jitter para reduzir tempestade de reconexões.
- Último estado válido é preservado quando ocorre timeout temporário.
- Sensores de diagnóstico adicionados:
  - Connection State: online/degraded/offline.
  - Consecutive Failures: número de falhas seguidas.
- Comandos de switch/select agora usam o scanner Bluetooth do Home Assistant e `bleak-retry-connector` com `max_retries` e timeout configurados.
- Escrita em select agora solicita refresh do coordinator após comando.
- Workflow GitHub Actions para validar sintaxe Python.

Observação importante:
- Alguns modelos/firmwares Bluetti novos usam protocolo criptografado ou mudado. Quando o firmware não expõe controle local compatível, a integração deve manter sensores e diagnóstico, mas controles podem continuar bloqueados até suporte na biblioteca base.
