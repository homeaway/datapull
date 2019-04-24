Create Table IF NOT exists Users_Destination ( UserId int primary key,
        UserGuid varchar(120),
        PasswordSalt varchar(120),
        Password varchar(240),
        PasswordEncryption varchar(120),
        PasswordResetFlag bit,
        PasswordModifiedDate datetime(3)
        );