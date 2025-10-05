<?php
include('config/boot.php');

$n_id = $_GET['n_id'] + 0; 

$db->query(sprintf("DELETE FROM ZMINNY_NPP WHERE N_ID=%d", $n_id));








$status->info("npp ".$n_id." deleted.");
echo $status->html();





//$status->info("mash deleted.");