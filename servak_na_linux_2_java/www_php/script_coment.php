<?php

include('config/boot.php');

$coment = $_GET['res'];

$res = '';

$res .= "./instr/".$lang."/".$coment;

$text = file_get_contents($res);
echo $text;