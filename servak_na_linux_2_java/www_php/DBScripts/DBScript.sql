--	inicBD

CREATE TABLE MASHYNES
(
	M_ID INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
	NAME VARCHAR(32) NOT NULL CHECK (NAME REGEXP '^[ -~]+$'),
	M1 TEXT NOT NULL CHECK (M1 REGEXP '^[ 0-9]+$'),
	M2 TEXT DEFAULT NULL CHECK (M2 REGEXP '^[ 0-9]+$'),
	PRIMARY KEY(M_ID)
	)
ENGINE=MyISAM DEFAULT CHARSET='utf8' AUTO_INCREMENT=1;


CREATE TABLE LIRY
(
	L_ID INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
	M_ID INT(11) UNSIGNED NOT NULL,
	NAME VARCHAR(32) DEFAULT NULL,
	MAGAZ TINYINT(1) UNSIGNED DEFAULT 1 CHECK (MAGAZ IN (1, 2)),
	FORM VARCHAR(98) NOT NULL CHECK (FORM REGEXP '^[()\\-+*\/.=0-9a-z]+$'),
	FORM_ZV VARCHAR(98) NOT NULL CHECK (FORM_ZV REGEXP '^[()\\-+*\/.=0-9a-z]+$'),
	BR_KOL_LIR TINYINT(1) UNSIGNED DEFAULT 4 CHECK (BR_KOL_LIR >= 2 AND BR_KOL_LIR <= 4),
	PRIMARY KEY(L_ID)
	)
ENGINE=MyISAM DEFAULT CHARSET='utf8' AUTO_INCREMENT=1;


CREATE TABLE ZMINNY
(
	Z_ID INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
	M_ID INT(11) UNSIGNED NOT NULL,
	NAME VARCHAR(32) DEFAULT NULL,
	BUKVA VARCHAR(1) NOT NULL CHECK (BUKVA REGEXP '^[e-hj-z]+$'),
	ZNACHENNJA FLOAT(48) DEFAULT NULL,
	NPP_S TINYINT(1) UNSIGNED DEFAULT 0 CHECK (NPP_S IN (0, 1, 2)),
	PRIMARY KEY(Z_ID)
	)
ENGINE=MyISAM DEFAULT CHARSET='utf8' AUTO_INCREMENT=1;


CREATE TABLE ZMINNY_NPP
(
	N_ID INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
	Z_ID INT(11) UNSIGNED NOT NULL,
	ZNACHENNJA FLOAT(48) NOT NULL,
	UMOVA VARCHAR(49) NOT NULL CHECK (UMOVA REGEXP '^[!-~]+$'),
	COMENT VARCHAR(99) NOT NULL,
	PRIMARY KEY(N_ID)
	)
ENGINE=MyISAM DEFAULT CHARSET='utf8' AUTO_INCREMENT=1;


CREATE TABLE UMOVY
(
	U_ID INT(11) UNSIGNED NOT NULL AUTO_INCREMENT,
	L_ID INT(11) UNSIGNED NOT NULL,
	UMOVA VARCHAR(98) NOT NULL CHECK (UMOVA REGEXP '^[!-~]+$'),
	PRIMARY KEY(U_ID)
	)
ENGINE=MyISAM DEFAULT CHARSET='utf8' AUTO_INCREMENT=1;



CREATE TABLE ZMINNY_LIRA
(
	ZL_ID INT(11) UNSIGNED PRIMARY KEY NOT NULL AUTO_INCREMENT,
	Z_ID INT(11) UNSIGNED NOT NULL,
	L_ID INT(11) UNSIGNED NOT NULL,
	M_ID INT(11) UNSIGNED NOT NULL
	)
ENGINE=MyISAM AUTO_INCREMENT=1;



INSERT INTO MASHYNES(M_ID, NAME, M1, M2)
VALUES
	(1, 'ZFWVG_250', '20 20 46 46 21 22 24 25 27 30 34 35 36 38 40 44 45 47 48 50 51 52 54 55 56 58 60 62 65 68 70 75 80 85 90 95 100 120 127 23 26 28 29 33 31 32 39 42 41 43 49 53 57 59 63 64 67 66 69 72 74 71 76 77 78 79 81 82 83 84 87 89 88 91 93 94 97 99 101 103', '21 22 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99 100'),
	(2, '53A80', '23 24 25 30 33 34 35 37 40 41 43 45 47 48 50 53 55 58 59 60 61 62 65 67 70 71 73 75 79 80 83 85 89 90 92 95 97 98 100 23 24 25 30 33 34 35 37 40 41 43 45 47 48 50 53 55 58 59 60 61 62 65 67 70 71 73 75 79 80 83 85 89 90 92 95 97 98 100 20 21 22 26 28 27 32 36 38 39 44 46 49 51 52 54 56 57 63 64 66 68 69 72 74 76 77 81 82 84 88 86 93 96 99 101 103', NULL);



INSERT INTO LIRY(L_ID, M_ID, NAME, MAGAZ, FORM, FORM_ZV, BR_KOL_LIR)
VALUES
	(1, 1, 'деление', 1, 'i=((n*g)*h)/(z*15)', 'z=((n*g)*h)/(i*15)', 4),
	(2, 1, 'лира на диференциала', 2, 'i=(n*(sin(u)))/(((6.28318530717958647692*h)*m)*g)', 'u=asin(((((6.28318530717958647692*h)*m)*g)*i)/n)', 4),
	(3, 2, 'delen', 1, 'i=((24*k)*n)/z', 'z=((24*k)*n)/i', 4),
	(4, 2, 'diferent na nis', 1, 'i=(7.95775*(sin(u)))/(m*k)', 'u=asin(((m*k)*i)/7.95775)', 4);



INSERT INTO ZMINNY(Z_ID, M_ID, NAME, BUKVA, ZNACHENNJA, NPP_S)
VALUES
	(1, 2, 'hodov na freza', 'k', 1, 0),
	(2, 2, 'zuby', 'z', NULL, 0),
	(3, 2, 'dop.lira', 'n', NULL, 1),
	(4, 2, 'kut', 'u', NULL, 0),
	(5, 2, 'modul', 'm', NULL, 0),
	(6, 1, 'бр.зъби', 'z', NULL, 0),
	(7, 1, 'бр.ходове на инстр.', 'g', 1, 0),
	(8, 1, 'диапазон', 'h', NULL, 2),
	(9, 1, 'глава на м-на', 'n', NULL, 2),
	(10, 1, 'ъгъл на наклона', 'u', NULL, 0),
	(11, 1, 'модул', 'm', NULL, 0);


INSERT INTO ZMINNY_NPP(N_ID, Z_ID, ZNACHENNJA, UMOVA, COMENT)
VALUES
	(1, 3, 1, '1=1', '75 / 75'),
	(2, 3, 2, 'z/k>=183', '50 / 100'),
	(3, 8, 1, '1=1', 'I'),
	(4, 8, 4, '1=1', 'II'),
	(5, 9, 25.4, '1=1', 'ApG - ApGS'),
	(6, 9, 63.5, '1=1', 'ApFW');


INSERT INTO UMOVY(U_ID, L_ID, UMOVA)
VALUES
	(1, 1, 'c+d>=61'),
	(2, 1, 'c+d<=155'),
	(3, 1, 'a+b>=72'),
	(4, 1, 'a+b<=138');

INSERT INTO UMOVY(L_ID, UMOVA)
VALUES
	(3, 'q:97<=a+b'),
	(3, 'w:a+b<=146'),
	(3, 'e:96<=c+d'),
	(3, 'r:c+d<=158'),
	(3, 't:146<=a+b'),
	(3, 'y:((158-(c+d))*1000)/62<=1000-((((a+b)-146)*1000)/23)'),
	(3, 'u:83<=a+b'),
	(3, 'i:a+b<=97'),
	(3, 'o:125<=c+d'),
	(3, 'p:c+d<=158'),
	(3, 's:c+d<=125'),
	(3, 'f:((125-(c+d))*1000)/29<=1000-(((97-(a+b))*1000)/14)'),
	(3, '(((q&w)&(e&r))|(((t&y)&r)|((u&i)&(o&p))))|((i&s)&f)'),
	(4, 'q:90<=a+b'),
	(4, 'w:a+b<=138'),
	(4, 'e:76<=c+d'),
	(4, 'r:c+d<=140'),
	(4, 't:72<=a+b'),
	(4, 'y:a+b<=90'),
	(4, 'u:87<=c+d'),
	(4, 'i:58<=a+b'),
	(4, 'o:a+b<=120'),
	(4, 'p:165<=c+d'),
	(4, 's:c+d<=190'),
	(4, 'f:88<=a+b'),
	(4, 'g:147<=c+d'),
	(4, 'h:c+d<=165'),
	(4, 'j:c+d>=140'),
	(4, 'k:(((c+d)-140)*1000)/35<=1000-(((138-(a+b))*1000)/66)'),
	(4, 'l:a+b<=90'),
	(4, 'z:c+d<=87'),
	(4, 'x:((87-(c+d))*1000)/11<=1000-(((90-(a+b))*1000)/18)'),
	(4, 'v:a+b<=88'),
	(4, 'n:((165-(c+d))*1000)/18<=1000-(((88-(a+b))*1000)/30)'),
	(4, '(((q&w)&(e&r))|(((t&y)&(u&r))|((i&o)&(p&s))))|((((f&o)&(g&h))|(n&(v&h)))|((x&(z&l))|(k&(j&w))))');


INSERT INTO ZMINNY_LIRA(ZL_ID, Z_ID, L_ID, M_ID)
VALUES
	(1, 6, 1, 1),
	(2, 7, 1, 1),
	(3, 8, 1, 1),
	(4, 9, 1, 1),
	(5, 7, 2, 1),
	(6, 8, 2, 1),
	(7, 9, 2, 1),
	(8, 11, 2, 1),
	(9, 10, 2, 1),
	(10, 2, 3, 2),
	(11, 1, 3, 2),
	(12, 3, 3, 2),
	(13, 4, 4, 2),
	(14, 5, 4, 2),
	(15, 1, 4, 2);

--	endinic