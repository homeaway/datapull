Create Table IF NOT EXISTS Users_Destination ( "UserId" int primary key,
        "UserGuid" varchar(100),
        "PasswordSalt" varchar(100),
        "Password" varchar(240),
        "PasswordEncryption" varchar(100),
        "PasswordResetFlag" boolean,
        "PasswordModifiedDate" timestamp
        );