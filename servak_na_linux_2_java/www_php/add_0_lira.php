<?php
include('config/boot.php');

$m_id = $_GET['m_id'] + 0; 

$result = $db->query(sprintf("
				SELECT COUNT(M_ID) AS LIRS
				FROM (
					SELECT M_ID
					FROM LIRY
					WHERE M_ID=%d
				) LL
				GROUP BY M_ID
				", $m_id));

if($result->num_rows > 0) {
	$lirs = $result->fetch_object()->LIRS;
	if($lirs >= 3) {
		echo "error: max 3 liry";
		exit();
	}
}


$db->query(sprintf("
				INSERT INTO LIRY(M_ID, FORM, FORM_ZV, MAGAZ, NAME, BR_KOL_LIR)
				VALUES
					(%d, 'i=x', 'x=i', 1, 'srakadupacurvamaty11', 4);
				", $m_id));


$result = $db->query(sprintf("
				SELECT L_ID
				FROM LIRY
				WHERE NAME='srakadupacurvamaty11' AND M_ID=%d
				", $m_id));

$l = $result->fetch_object();
$l_id = $l->L_ID;

$db->query(sprintf("
				UPDATE LIRY SET NAME='new lire'
				WHERE L_ID=%d
				", $l_id));

echo $l_id;