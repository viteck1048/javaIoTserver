<?php
include('config/boot.php');

$l_id = $_GET['l_id'] + 0;

$result = $db->query(sprintf("
					SELECT M_ID
					FROM LIRY
					WHERE L_ID=%d
					", $l_id));

$m = $result->fetch_object();
$m_id = $m->M_ID;



$zm_npp_id_arr[] = array();
$zm_id_arr[] = array();

$result = $db->query(sprintf("
					SELECT Z_ID
					FROM
						(SELECT *
						FROM
							(SELECT ZL_ID, Z_ID, L_ID
							FROM ZMINNY_LIRA ZMLR1
							WHERE M_ID=%d) ZMLR2
						GROUP BY Z_ID
						HAVING COUNT(Z_ID)=1) ZMLR3
					WHERE L_ID=%d", $m_id, $l_id));


while($q = $result->fetch_object()) {
	$zm_id_arr[] = $q->Z_ID;
}

$result = $db->query(sprintf("
					SELECT ZMLR4.Z_ID AS Z_ID
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
						WHERE L_ID=%d) ZMLR4
					LEFT JOIN ZMINNY ZM
					ON ZMLR4.Z_ID=ZM.Z_ID
					WHERE ZM.NPP_S!=0", $m_id, $l_id));


while($q = $result->fetch_object()) {
	$zm_npp_id_arr[] = $q->Z_ID;
}

$db->query(sprintf("DELETE FROM UMOVY WHERE L_ID=%d", $l_id));

$db->query(sprintf("DELETE FROM ZMINNY_LIRA WHERE L_ID=%d", $l_id));

foreach($zm_npp_id_arr as $zz) {
	$db->query(sprintf("DELETE FROM ZMINNY_NPP WHERE Z_ID=%d", $zz));
}
foreach($zm_id_arr as $zz) {
	$db->query(sprintf("DELETE FROM ZMINNY WHERE Z_ID=%d", $zz));
}
unset($zz);

$db->query(sprintf("DELETE FROM LIRY WHERE L_ID=%d", $l_id));

header("Location: ./index.php?m_id=".$m_id);
