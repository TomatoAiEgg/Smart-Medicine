const appKey = pm.environment.get('appKey') || 'demo-app';
const appSecret = pm.environment.get('appSecret');

if (!appSecret) {
  throw new Error('Missing Apifox environment variable: appSecret');
}

const timestamp = Math.floor(Date.now() / 1000).toString();
const rawBody = pm.request.body && pm.request.body.raw ? pm.request.body.raw : '';
const bodyHash = CryptoJS.SHA256(rawBody).toString(CryptoJS.enc.Hex);
const source = `${appKey}\n${timestamp}\n${bodyHash}`;
const signature = CryptoJS.HmacSHA256(source, appSecret).toString(CryptoJS.enc.Hex);

pm.environment.set('timestamp', timestamp);
pm.environment.set('signature', signature);
pm.request.headers.upsert({ key: 'X-App-Key', value: appKey });
pm.request.headers.upsert({ key: 'X-Timestamp', value: timestamp });
pm.request.headers.upsert({ key: 'X-Signature', value: signature });
pm.request.headers.upsert({ key: 'Content-Type', value: 'application/json' });
