require('dotenv').config();
const { Pool } = require('pg');

module.exports = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: { rejectUnauthorized: false },
  max: 5, // la base est partagée par 4 services : on limite les connexions
});
