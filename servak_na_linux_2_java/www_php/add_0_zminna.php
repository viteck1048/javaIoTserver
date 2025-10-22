<?php
include('config/boot.php');

$l_id = $_GET['l_id'] + 0; 
$bukva = "'".$_GET['bukva']."'";
$zm_poz = $_GET['zm_poz'] + 0;

$result = $db->query(sprintf("
					SELECT M_ID
					FROM LIRY
					WHERE L_ID=%d
					", $l_id));

$m = $result->fetch_object();
$m_id = $m->M_ID;

$result = $db->query(sprintf("
					SELECT Z_ID
					FROM ZMINNY
					WHERE M_ID=%d AND BUKVA=%s
					", $m_id, $bukva));

if($result->num_rows > 0) {
	$z = $result->fetch_object();
	$z_id = $z->Z_ID;
	$result = $db->query(sprintf("
						SELECT ZL_ID
						FROM ZMINNY_LIRA
						WHERE M_ID=%d AND L_ID=%d AND Z_ID=%d
						", $m_id, $l_id, $z_id));

	if($result->num_rows == 0) {
		$db->query(sprintf("
					INSERT INTO ZMINNY_LIRA(Z_ID, L_ID, M_ID)
					VALUES
						(%d, %d, %d);
					", $z_id, $l_id, $m_id));
	}
	
	echo sprintf("{\"z_id\":%d,\"zm_poz\":%d}", $z_id, $zm_poz);
	
	exit();
}


$db->query(sprintf("
				INSERT INTO ZMINNY(M_ID, BUKVA, NPP_S)
				VALUES
					(%d, %s, 0);
				", $m_id, $bukva));
$result = $db->query(sprintf("
				SELECT Z_ID
				FROM ZMINNY
				WHERE BUKVA=%s AND M_ID=%d
				", $bukva, $m_id));

if($result->num_rows == 1) {
	$z = $result->fetch_object();
	$z_id = $z->Z_ID;

	$db->query(sprintf("
					INSERT INTO ZMINNY_LIRA(Z_ID, L_ID, M_ID)
					VALUES
						(%d, %d, %d);
					", $z_id, $l_id, $m_id));
	
	echo sprintf("{\"z_id\":%d,\"zm_poz\":%d}", $z_id, $zm_poz);

}
