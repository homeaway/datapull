Create Table IF NOT EXISTS Users_Source ( UserId int primary key,
        UserGuid varchar(120),
        PasswordSalt varchar(120),
        Password varchar(240),
        PasswordEncryption varchar(120),
        PasswordResetFlag bit,
        PasswordModifiedDate datetime(3)
        );

INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(1, '8f719ace879743268e5c29edf3fc889b', 'UNUSED', 'UMS', 'UMS', 1, '2015-09-29 15:48:00.072');
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(2, '067028f8498249a9adb27fa6df67e257', 'UNUSED', '$2a$04$uTVvazCWID661gZL.zwOgufo/HpKB4qaYoYtylCtWjRFhguW/sTXq', 'B_UMS_NORMAL', 1, '2014-03-25 16:21:51.417') ;
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(3, 'fd1a20888b524c938f9d05fd26f9f150', 'UNUSED', '$2a$04$g4b/qc7nTlBFbN.DZCr2DeZOz9HYe/0ohjWbDKJllbEhiRNGaPBqS', 'B_UMS_NORMAL', 1, '2014-03-25 16:21:51.423') ;
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(4, '7f8e18c099174c268595c2a277624fe1', 'UNUSED', '$2a$04$fUUE1.IHO.i2E8FJ5XAnt.3t22RHGElPRx9ALipAAh8yRfHJIKrmi', 'B_UMS_NORMAL', 1, '2014-03-25 16:21:51.437') ;
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(5, '9ad63308062e49f4b888284af9fc0ff8', 'UNUSED', '$2a$04$pU0HqN3LAR6izBbzXeVpo.RetuKORGvAo/dxvlXpXDCaNJfxm/lMW', 'B_UMS_NORMAL', 1, '2014-03-25 16:21:51.437') ;
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(6, 'eac137afa4c343a6817d72784c43e3b0', 'UNUSED', '$2a$04$h3K2cohvNBLTv46gQwV/5ePCtdkXHtboX.0MVixW2QuNHrf/B.DYO', 'B_UMS_NORMAL', 1, '2014-03-25 16:21:51.453') ;
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(7, '17de72b2ca354b8b9a72c023477e13c7', 'UNUSED', '$2a$04$P7WlNwP79bwPvyrB0kywxeh3T3RRMla6anSQ0Y8EFOfnmf6yjaVw2', 'B_UMS_NORMAL', 1, '2014-03-25 16:21:51.466') ;
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(8, 'fe3c8f650f25415aaced01ad8bbe2899', 'USED', '$2a$04$/1CXd4QY9ijO6CEa896IbemXM7ZOQ9rlonM1e8RWt8y4qGbex6Woq', 'B_UMS_NORMAL', 1, '2014-03-28 22:23:22.557')  ;
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(9, 'fe02a99b51014a3fae0248d55bc4ab29', 'UNUSED', '$2a$04$yiS/1au5oqDZSkLJtncp6.uxe7TEnw/gmWuVgMAQEbiZ7N2EXfeiu', 'B_UMS_NORMAL', 0, '2014-03-25 16:21:51.475')  ;
INSERT IGNORE INTO Users_Source
(UserId, UserGuid, PasswordSalt, Password, PasswordEncryption, PasswordResetFlag, PasswordModifiedDate)
VALUES(10, '1af7a0c6fc31468da51c7085348e5060', 'UNUSED', '$2a$04$3hyudHGxYDDLsTfV8tFZLunl0tsiHRsXzd.7mOBmzfj1i2k2gNRDS', 'B_UMS_NORMAL', 0, '2014-03-25 16:21:51.477');


Create Table IF NOT EXISTS Users_Type (UserId int primary key, UserType varchar(60));

INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(1, 'admin');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(2, 'superadmin');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(3, 'networkadmin');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(4, 'Developer');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(5, 'Tester');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(6, 'Deveops');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(7, 'Architect');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(8, 'SysAdmin');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(9, 'Vendor');
INSERT IGNORE INTO Users_Type (UserId, UserType) VALUES(10, 'Security');