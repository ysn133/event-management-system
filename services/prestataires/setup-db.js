const fs = require('fs');
const path = require('path');
const pool = require('./db');

async function applySchema() {
  const schemaPath = path.resolve(__dirname, '../../database/prestataires.sql');

  try {
    const schema = fs.readFileSync(schemaPath, 'utf8');
    await pool.query(schema);
    console.log('Schéma prestataires appliqué.');
  } finally {
    await pool.end();
  }
}

applySchema().catch((error) => {
  console.error('Échec de l’application du schéma prestataires :', error.message);
  process.exitCode = 1;
});
