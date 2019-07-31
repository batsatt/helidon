CREATE TABLE AUTHOR (
    ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    NAME VARCHAR(64) NOT NULL
);

CREATE TABLE MICROBLOG (
    ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    AUTHOR_ID INT NOT NULL,
    NAME VARCHAR(64) NOT NULL,
    FOREIGN KEY (AUTHOR_ID) REFERENCES AUTHOR(ID)
);

CREATE TABLE CHIRP (
    ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    MICROBLOG_ID INT NOT NULL,
    CONTENT VARCHAR(140) NOT NULL,
    FOREIGN KEY (MICROBLOG_ID) REFERENCES MICROBLOG(ID)
);
