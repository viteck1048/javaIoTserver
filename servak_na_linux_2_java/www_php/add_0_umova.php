<?php
include('config/boot.php');

$l_id = $_GET['l_id'] + 0; 

$db->query(sprintf("
				INSERT INTO UMOVY(L_ID, UMOVA)
				VALUES
					(%d, 'qqqqqqqqqqqqqqqqq');
				", $l_id));
$result = $db->query("
				SELECT U_ID
				FROM UMOVY
				WHERE UMOVA='qqqqqqqqqqqqqqqqq'
				");

$u = $result->fetch_object();
$u_id = $u->U_ID;

$db->query(sprintf("
				UPDATE UMOVY SET UMOVA='1=1'
				WHERE U_ID=%d
				", $u_id));

echo $u_id;