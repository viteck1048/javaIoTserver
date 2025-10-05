<?php

include 'util.php';

$servername = "localhost";
$username = "debuser";
$password = "wqwqwq11";
$database = "lira_molly";

error_reporting (E_ALL ^ E_NOTICE);

$conn = mysqli_connect($servername, $username, $password);
if(!$conn) {
	die("Помилка з'єднання з сервером MySQL: " . mysqli_connect_error());
}

$result = mysqli_query($conn, "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '$database'");
if(mysqli_num_rows($result) == 0) {
	
	$sqlCreateDatabase = "CREATE DATABASE IF NOT EXISTS $database";
	
	if(mysqli_query($conn, $sqlCreateDatabase)) {
		echo "База даних $database успішно створена<br>Сторінка автоматично перезавантажиться за 5 секунд";
	}else {
		echo "Помилка створення бази даних: " . mysqli_error($conn);
	}	
	
	mysqli_close($conn);
	$db = mysqli_connect($servername, $username, $password, $database);
	$sqlScript = file_get_contents("DBScripts/DBScript.sql");
	$sqlQueries = explode(";", $sqlScript);
	foreach($sqlQueries as $query) {
		if(!empty(trim($query))) {
			$result = mysqli_query($db, $query);
			if(!$result) {
				echo "Помилка виконання SQL-запиту: " . mysqli_error($db);
			}
		}
	}
	echo "<script>setTimeout(function() {location.reload();}, 5000);</script>";
	
	mysqli_close($db);
	exit;
}

mysqli_select_db($conn, $database);

mysqli_close($conn);

$db = new Mysqli($servername, $username, $password, $database);

$status = new Status();
