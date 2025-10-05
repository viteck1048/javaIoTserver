<?php
include('config/boot.php');

$m_id = $_GET['m_id'] + 0;
$name = $_GET['name'];

$dbcursor = $db->query("
	SELECT *
	FROM MASHYNES
	WHERE M_ID = '$m_id'
	");

$ee = $dbcursor->fetch_object();
$mash = $ee;

$dbcursor = $db->query("
	SELECT *
	FROM LIRY
	WHERE M_ID = '$m_id'
	ORDER BY L_ID ASC
	");

$liry = array();
while($l = $dbcursor->fetch_object()) {
	$liry[] = $l;
}


$response = new stdClass();
$response->name1 = $name;
$response->mash = $mash;
$response->liry = $liry;
$liry_zm_arr = array();

foreach($liry as $l) {
	$dbcursor = $db->query("
		SELECT Z.Z_ID, Z.NAME, Z.NPP_S, Z.ZNACHENNJA, Z.BUKVA
		FROM ZMINNY_LIRA ZL
		LEFT JOIN ZMINNY Z
		ON ZL.Z_ID=Z.Z_ID
		WHERE ZL.L_ID=$l->L_ID
		ORDER BY ZL.ZL_ID ASC");
	$lr_zm = array();
	while($ee = $dbcursor->fetch_object()) {
		$lr_zm[] = $ee;
	}
	$liry_zm_arr[] = $lr_zm;
}


$response->liry_zm_arr = $liry_zm_arr;

echo json_encode($response);
?>
