Create Table Users ( "UserId" int primary key,
        "UserGuid" varchar(120),
        "PasswordSalt" varchar(120),
        "Password" varchar(120),
        "PasswordEncryption" varchar(120),
        "PasswordResetFlag" NUMBER(1),
        "PasswordModifiedDate" timestamp
        );