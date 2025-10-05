<?php
include('config/boot.php');

$l_id = $_GET['l_id'] + 0;
$z_id = $_GET['z_id'] + 0;

$result = $db->query(sprintf("
					SELECT M_ID
					FROM LIRY
					WHERE L_ID=%d
					", $l_id));

$m = $result->fetch_object();
$m_id = $m->M_ID;


$result = $db->query(sprintf("
					SELECT ZMLR4.Z_ID AS Z_ID, ZM.NPP_S AS NPP_S
					FROM
						(SELECT Z_ID
						FROM
							(SELECT *
							FROM
								(SELECT ZL_ID, Z_ID, L_ID
								FROM ZMINNY_LIRA ZMLR1
								WHERE M_ID=%d) ZMLR2
							GROUP BY Z_ID
							HAVING COUNT(Z_ID)=1) ZMLR3
						WHERE L_ID=%d AND Z_ID=%d) ZMLR4
					LEFT JOIN ZMINNY ZM
					ON ZMLR4.Z_ID=ZM.Z_ID", $m_id, $l_id, $z_id));


while($q = $result->fetch_object()) {
	$db->query(sprintf("DELETE FROM ZMINNY WHERE Z_ID=%d", $q->Z_ID));
	if($q->NPP_S != 0) {
		$db->query(sprintf("DELETE FROM ZMINNY_NPP WHERE Z_ID=%d", $q->Z_ID));
	}
}

$db->query(sprintf("DELETE FROM ZMINNY_LIRA WHERE L_ID=%d AND Z_ID=%d", $l_id, $z_id));






//$db->query(sprintf("DELETE FROM UMOVY WHERE L_ID=%d", $l_ID));














//$status->info("mash deleted.");