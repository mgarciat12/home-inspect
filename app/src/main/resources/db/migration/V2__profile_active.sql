-- Local mirror of the account state: a disabled user or a changed role ends the app session
ALTER TABLE user_profile ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
