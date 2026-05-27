const fs = require('fs');
const path = require('path');

function walk(dir) {
    let results = [];
    if (!fs.existsSync(dir)) return results;
    const list = fs.readdirSync(dir);
    list.forEach(function(file) {
        file = path.join(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) {
            if (!file.includes('build')) {
                results = results.concat(walk(file));
            }
        } else {
            results.push(file);
        }
    });
    return results;
}

const targetDir = path.join(process.cwd(), 'app', 'src');
const files = walk(targetDir);

files.forEach(file => {
    if (file.endsWith('.java') || file.endsWith('.xml')) {
        const content = fs.readFileSync(file, 'utf8');
        if (content.includes('<truncated')) {
            console.log('Truncated file found:', file);
        }
    }
});
