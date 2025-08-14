PWD=$(pwd)
echo $PWD
sed -i -e "s#root=./web#root=$PWD/web#g" scrubber/config/www-server.properties

