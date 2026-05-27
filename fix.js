const fs = require('fs'); 
const file = 'app/src/main/res/layout/activity_qr_scanner.xml';
let content = fs.readFileSync(file, 'utf8');
if (content.startsWith('"')) {
    content = content.slice(1, -2).replace(/\\n/g, '\n').replace(/\\"/g, '"');
    fs.writeFileSync(file, content, 'utf8');
    console.log('Fixed qr scanner xml');
}
