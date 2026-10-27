CREATE TABLE sf_identity_bootstrap_lock (
  id varchar(36) primary key
);

INSERT INTO sf_identity_bootstrap_lock (id) VALUES ('superadmin');
