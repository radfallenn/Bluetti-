module.exports = function (RED) {
  const noble = require('@abandonware/noble');

  const NOTIFY_UUID = 'ff01';
  const WRITE_UUID = 'ff02';

  function crc16(buf) {
    let crc = 0xffff;
    for (const b of buf) {
      crc ^= b;
      for (let i = 0; i < 8; i++) {
        crc = (crc & 1) ? ((crc >> 1) ^ 0xA001) : (crc >> 1);
      }
    }
    return crc & 0xffff;
  }

  function withCrc(bytes) {
    const body = Buffer.from(bytes);
    const crc = crc16(body);
    return Buffer.concat([body, Buffer.from([crc & 0xff, (crc >> 8) & 0xff])]);
  }

  function crcOk(buf) {
    if (!buf || buf.length < 3) return false;
    const got = buf[buf.length - 2] | (buf[buf.length - 1] << 8);
    const calc = crc16(buf.slice(0, -2));
    return got === calc;
  }

  function readCmd(address, qty) {
    return withCrc([1, 3, (address >> 8) & 0xff, address & 0xff, (qty >> 8) & 0xff, qty & 0xff]);
  }

  function writeCmd(address, value) {
    return withCrc([1, 6, (address >> 8) & 0xff, address & 0xff, (value >> 8) & 0xff, value & 0xff]);
  }

  function BluettiDirectNode(config) {
    RED.nodes.createNode(this, config);
    const node = this;

    node.name = config.name;
    node.mac = String(config.mac || '').toLowerCase();
    node.interval = Math.max(15, Number(config.interval || 30));
    node.autoStart = config.autoStart !== false;
    node.peripheral = null;
    node.writeChar = null;
    node.notifyChar = null;
    node.timer = null;
    node.response = Buffer.alloc(0);
    node.data = {
      battery: null,
      acInputW: null,
      dcInputW: null,
      acOutputW: null,
      dcOutputW: null,
      acEnabled: null,
      dcEnabled: null,
      online: false,
      updatedAt: null
    };

    function emitError(message, extra) {
      node.send([null, { payload: { error: message, extra: extra || null, ts: new Date().toISOString() } }]);
      node.status({ fill: 'red', shape: 'ring', text: message });
    }

    function emitData() {
      node.data.online = true;
      node.data.updatedAt = new Date().toISOString();
      node.send([{ payload: node.data }, null]);
      node.status({ fill: 'green', shape: 'dot', text: `bat ${node.data.battery ?? '--'}%` });
    }

    function parseRead(buf) {
      if (!crcOk(buf)) return emitError('crc invalido', buf.toString('hex'));
      if (buf[0] !== 1 || buf[1] !== 3) return;
      const byteCount = buf[2];
      const words = [];
      for (let i = 0; i < byteCount / 2; i++) words.push((buf[3 + i * 2] << 8) | buf[4 + i * 2]);

      if (words.length === 8) {
        node.data.dcInputW = words[0];
        node.data.acInputW = words[1];
        node.data.acOutputW = words[2];
        node.data.dcOutputW = words[3];
        node.data.battery = words[7];
        setTimeout(() => sendRead(3007, 2), 250);
      } else if (words.length === 2) {
        node.data.acEnabled = words[0] === 1;
        node.data.dcEnabled = words[1] === 1;
        emitData();
      }
    }

    function onNotify(data) {
      node.response = Buffer.concat([node.response, data]);
      if (node.response.length > 512) node.response = Buffer.alloc(0);
      const b = node.response;
      if (b.length >= 5 && b[0] === 1 && b[1] === 3) {
        const total = b[2] + 5;
        if (b.length >= total) {
          const pkt = b.slice(0, total);
          node.response = Buffer.alloc(0);
          parseRead(pkt);
        }
      } else if (b.length >= 8 && b[0] === 1 && b[1] === 6) {
        node.response = Buffer.alloc(0);
        setTimeout(readAll, 500);
      }
    }

    function write(buf) {
      if (!node.writeChar) return emitError('nao conectado');
      node.writeChar.write(buf, false, err => {
        if (err) emitError('erro ao escrever', err.message);
      });
    }

    function sendRead(address, qty) { write(readCmd(address, qty)); }
    function writeRegister(address, value) { write(writeCmd(address, value)); }
    function readAll() { sendRead(36, 8); }

    function startTimer() {
      clearInterval(node.timer);
      node.timer = setInterval(readAll, node.interval * 1000);
      readAll();
    }

    function connect(peripheral) {
      node.peripheral = peripheral;
      noble.stopScanning();
      node.status({ fill: 'yellow', shape: 'dot', text: 'conectando' });
      peripheral.connect(err => {
        if (err) return emitError('falha ao conectar', err.message);
        peripheral.discoverSomeServicesAndCharacteristics([], [NOTIFY_UUID, WRITE_UUID], (err2, services, chars) => {
          if (err2) return emitError('erro nos servicos', err2.message);
          node.notifyChar = chars.find(c => c.uuid === NOTIFY_UUID);
          node.writeChar = chars.find(c => c.uuid === WRITE_UUID);
          if (!node.notifyChar || !node.writeChar) return emitError('caracteristicas ff01/ff02 nao encontradas');
          node.notifyChar.on('data', onNotify);
          node.notifyChar.subscribe(err3 => {
            if (err3) return emitError('erro ao ativar notify', err3.message);
            startTimer();
          });
        });
      });
      peripheral.once('disconnect', () => {
        node.data.online = false;
        node.status({ fill: 'red', shape: 'ring', text: 'offline' });
        clearInterval(node.timer);
      });
    }

    function scanAndConnect() {
      if (!node.mac) return emitError('mac nao configurado');
      if (noble.state !== 'poweredOn') {
        node.status({ fill: 'yellow', shape: 'ring', text: `bluetooth ${noble.state}` });
        return;
      }
      node.status({ fill: 'blue', shape: 'dot', text: 'procurando' });
      noble.removeAllListeners('discover');
      noble.on('discover', p => {
        if (String(p.address || '').toLowerCase() === node.mac) connect(p);
      });
      noble.startScanning([], false, err => {
        if (err) emitError('erro no scan', err.message);
      });
      setTimeout(() => noble.stopScanning(), 15000);
    }

    noble.on('stateChange', state => {
      if (state === 'poweredOn' && node.autoStart) scanAndConnect();
      else if (state !== 'poweredOn') node.status({ fill: 'yellow', shape: 'ring', text: `bluetooth ${state}` });
    });

    node.on('input', msg => {
      const cmd = (msg.payload && msg.payload.command) || msg.command || msg.payload;
      if (cmd === 'connect') scanAndConnect();
      else if (cmd === 'read') readAll();
      else if (cmd === 'ac_on') writeRegister(3007, 1);
      else if (cmd === 'ac_off') writeRegister(3007, 0);
      else if (cmd === 'dc_on') writeRegister(3008, 1);
      else if (cmd === 'dc_off') writeRegister(3008, 0);
      else emitError('comando desconhecido', cmd);
    });

    node.on('close', done => {
      clearInterval(node.timer);
      try { noble.stopScanning(); } catch (e) {}
      if (node.peripheral && node.peripheral.state === 'connected') node.peripheral.disconnect(() => done());
      else done();
    });

    if (node.autoStart && noble.state === 'poweredOn') setTimeout(scanAndConnect, 1000);
  }

  RED.nodes.registerType('bluetti-direct', BluettiDirectNode);
};
