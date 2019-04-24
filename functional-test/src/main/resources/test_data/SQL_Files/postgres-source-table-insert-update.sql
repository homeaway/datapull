INSERT INTO Users
("UserId", "UserGuid", "PasswordSalt", "Password", "PasswordEncryption", "PasswordResetFlag", "PasswordModifiedDate")
VALUES(11, '00000000-0000-0000-0000-000000000000', 'USED', 'UMS', 'UMS', true, '2018-10-29 15:48:00.071') ON CONFLICT ("UserId") DO NOTHING;

INSERT INTO Users
("UserId", "UserGuid", "PasswordSalt", "Password", "PasswordEncryption", "PasswordResetFlag", "PasswordModifiedDate")
VALUES(12, '00000000-0000-0000-0000-000000000000', 'NOTUSED', 'Sample', 'UMS', true, '2010-02-19 00:48:00.071') ON CONFLICT ("UserId") DO NOTHING;

UPDATE Users set "UserGuid"='00000000-0000-0000-0000-000000000000' WHERE "UserId" = 6;

UPDATE Users set "Password"='#passwordchanged)$' WHERE "UserId" = 7;

UPDATE Users set "PasswordSalt"='Updated' where "UserId" = 8;

UPDATE Users set "PasswordResetFlag"=true where "UserId" = 9;

UPDATE Users set "PasswordEncryption" = 'modified' where "UserId" = 5;
