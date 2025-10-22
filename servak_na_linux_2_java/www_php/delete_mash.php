<?php
include('config/boot.php');

$m_id = $_GET['m_id'] + 0; 

$result = $db->query(sprintf("
					SELECT L_ID
					FROM LIRY
					WHERE M_ID=%d", $m_id));

while($q = $result->fetch_object()) {
	$db->query(sprintf("DELETE FROM UMOVY WHERE L_ID=%d", $q->L_ID));
}


$result = $db->query(sprintf("
					SELECT Z_ID
					FROM ZMINNY
					WHERE M_ID=%d AND NPP_S!=0", $m_id));


while($q = $result->fetch_object()) {
	$db->query(sprintf("DELETE FROM ZMINNY_NPP WHERE Z_ID=%d", $q->Z_ID));
}

$db->query(sprintf("DELETE FROM ZMINNY_LIRA WHERE M_ID=%d", $m_id));
$db->query(sprintf("DELETE FROM ZMINNY WHERE M_ID=%d", $m_id));
$db->query(sprintf("DELETE FROM LIRY WHERE M_ID=%d", $m_id));
$db->query(sprintf("DELETE FROM MASHYNES WHERE M_ID=%d", $m_id));



















$status->info("mash deleted.");
//$status->info("SQL: $sql");
//echo $status->html();
header("Location: ./");