<?php
include('config/boot.php');

$z_id = $_GET['z_id'] + 0; 

$db->query(sprintf("
				INSERT INTO ZMINNY_NPP(Z_ID, ZNACHENNJA, UMOVA, COMENT)
				VALUES
					(%d, 1, '1=1', 'srakadupakurvamaty');
				", $z_id));
$result = $db->query("
				SELECT N_ID
				FROM ZMINNY_NPP
				WHERE COMENT='srakadupakurvamaty'
				");

$n = $result->fetch_object();
$n_id = $n->N_ID;

$db->query(sprintf("
				UPDATE ZMINNY_NPP SET COMENT='положення %d'
				WHERE N_ID=%d
				", $n_id, $n_id));

echo $n_id;