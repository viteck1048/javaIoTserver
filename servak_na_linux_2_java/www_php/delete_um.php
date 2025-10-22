<?php
include('config/boot.php');

$u_id = $_GET['u_id'] + 0; 

$db->query(sprintf("DELETE FROM UMOVY WHERE U_ID=%d", $u_id));














$status->info("umova deleted.");
echo $status->html();