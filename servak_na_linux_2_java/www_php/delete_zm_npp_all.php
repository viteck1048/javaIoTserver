<?php
include('config/boot.php');

$z_id = $_GET['z_id'] + 0;

$db->query(sprintf("DELETE FROM ZMINNY_NPP WHERE Z_ID=%d", $z_id));


$db->query(sprintf("UPDATE ZMINNY SET NPP_S=0 WHERE Z_ID=%d", $z_id));




$status->info("z_id ".$z_id." npp all deleted.");
echo $status->html();

