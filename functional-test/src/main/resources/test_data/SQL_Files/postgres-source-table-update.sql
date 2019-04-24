UPDATE Users set "UserGuid"='00000000-0000-0000-0000-000000000000' WHERE "UserId" = 1;
UPDATE Users set "Password"='#passwordmodified$' WHERE "UserId" = 2;
UPDATE Users set "PasswordSalt"='Changed' where "UserId" = 3;
UPDATE Users set "PasswordResetFlag"=false where "UserId" = 4;
UPDATE Users set "PasswordModifiedDate" = '2019-01-01 23:21:31.437' where "UserId" = 5;
