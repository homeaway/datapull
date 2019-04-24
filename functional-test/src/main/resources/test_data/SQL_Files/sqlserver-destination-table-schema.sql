IF NOT EXISTS(SELECT * FROM sys.tables WHERE name = 'DataPull_Users_Destination')
  Begin
  CREATE TABLE dbo.DataPull_Users_Destination (
    UserId int PRIMARY KEY ,
    UserGuid varchar(32),
    PasswordSalt varchar(128),
    Password varchar(75),
    PasswordEncryption varchar(32),
    PasswordResetFlag bit,
    PasswordModifiedDate datetime2
  )
  END;