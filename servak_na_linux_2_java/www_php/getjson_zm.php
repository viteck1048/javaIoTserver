<?php
include('config/boot.php');

$z_id = $_GET['z_id'] + 0;
$zm_poz = $_GET['zm_poz'] + 0;

$dbcursor = $db->query("
	SELECT *
	FROM ZMINNY
	WHERE Z_ID = '$z_id'
	");


if($u = $dbcursor->fetch_object()) {
	$zm = $u;
}

$response = new stdClass();

$response->zm = $zm;
$response->zm_poz = $zm_poz;

echo json_encode($response);
?>
