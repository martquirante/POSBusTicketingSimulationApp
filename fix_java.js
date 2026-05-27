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
            results = results.concat(walk(file));
        } else {
            results.push(file);
        }
    });
    return results;
}

const javaDir = path.join(process.cwd(), 'app', 'src', 'main', 'java');
const files = walk(javaDir);

files.forEach(file => {
    if (file.endsWith('.java')) {
        const content = fs.readFileSync(file, 'utf8');
        if (content.startsWith('"package ')) {
            try {
                const parsed = JSON.parse(content);
                fs.writeFileSync(file, parsed, 'utf8');
                console.log('Fixed Java:', file);
            } catch (e) {
                // If it fails because of unescaped chars, fallback to manual unescape
                try {
                    let cleaned = content.slice(1, -2).replace(/\\n/g, '\n').replace(/\\"/g, '"');
                    fs.writeFileSync(file, cleaned, 'utf8');
                    console.log('Fixed Java manually:', file);
                } catch(e2) {
                     console.error('Error fixing:', file, e2.message);
                }
            }
        }
    }
});
