#!/usr/bin/env bash
set -e

echo "========================================"
echo " Bluetti Bridge - Instalador Raspberry"
echo "========================================"

REPO="https://github.com/radfallenn/Bluetti-.git"
BASE="$HOME/Bluetti-"
APP="$BASE/raspberry-bridge"

if ! command -v git >/dev/null 2>&1; then
  echo "Instalando git..."
  sudo apt-get update
  sudo apt-get install -y git
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker não encontrado. Instale pelo CasaOS ou Docker oficial antes."
  exit 1
fi

if [ ! -d "$BASE/.git" ]; then
  echo "Baixando repositório..."
  git clone "$REPO" "$BASE"
else
  echo "Atualizando repositório..."
  cd "$BASE"
  git pull
fi

cd "$APP"

echo "Verificando arquivos..."
ls -la

if [ ! -f docker-compose.yml ]; then
  echo "ERRO: docker-compose.yml não encontrado em $APP"
  exit 1
fi

if [ ! -S /var/run/dbus/system_bus_socket ]; then
  echo "Atenção: DBus do sistema não encontrado em /var/run/dbus/system_bus_socket"
fi

echo "Parando container antigo..."
sudo docker compose down || true

echo "Liberando Bluetooth..."
sudo rfkill unblock bluetooth || true
sudo systemctl restart bluetooth || true
sleep 2

echo "Construindo e iniciando Bluetti Bridge..."
sudo docker compose up -d --build

echo "Aguardando serviço subir..."
sleep 8

echo "Status do container:"
sudo docker compose ps

echo "Logs recentes:"
sudo docker compose logs --tail=80

echo "Porta 5055:"
sudo ss -tulpn | grep 5055 || true

IP=$(hostname -I | awk '{print $1}')
echo "========================================"
echo " Acesse no navegador: http://$IP:5055"
echo "========================================"
