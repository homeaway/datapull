CREATE Table IF NOT exists ${keyspace}.${table} ("UserId" int primary key,
"UserGuid" varchar,
"PasswordSalt" varchar,
"Password" varchar,
"PasswordEncryption" varchar,
"PasswordResetFlag" boolean,
"PasswordModifiedDate" timestamp
);