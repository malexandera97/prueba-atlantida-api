CREATE ROLE banco WITH LOGIN PASSWORD 'banco';

CREATE DATABASE prueba_atlantida OWNER banco ENCODING 'UTF8';

CREATE DATABASE prueba_atlantida_test OWNER banco ENCODING 'UTF8';
