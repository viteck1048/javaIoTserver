<?php
include('config/boot.php');

$l_id = $_GET['l_id'] + 0;

$dbcursor = $db->query("
	SELECT *
	FROM UMOVY
	WHERE L_ID = '$l_id'
	ORDER BY U_ID ASC
	");

$umovy = array();
while($u = $dbcursor->fetch_object()) {
	$umovy[] = $u;
}


$response = new stdClass();

$response->l_id = $l_id;
$response->umovy = $umovy;

echo json_encode($response);
?>
