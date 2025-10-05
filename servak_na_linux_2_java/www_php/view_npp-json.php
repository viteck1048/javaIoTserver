<?php
include('config/boot.php');

$z_id = $_GET['z_id'] + 0;
$l_id = $_GET['l_id'] + 0;

$dbcursor = $db->query("
	SELECT *
	FROM ZMINNY_NPP
	WHERE Z_ID = '$z_id'
	ORDER BY N_ID ASC
	");

$zm_npp = array();
while($n = $dbcursor->fetch_object()) {
	$zm_npp[] = $n;
}

$dbcursor = $db->query("
	SELECT NPP_S
	FROM ZMINNY
	WHERE Z_ID = '$z_id'
	");

$n = $dbcursor->fetch_object();
$npp_s = $n->NPP_S + 0;


$response = new stdClass();

$response->z_id = $z_id;
$response->l_id = $l_id;
$response->npp_s = $npp_s;
$response->zm_npp = $zm_npp;

echo json_encode($response);
?>
