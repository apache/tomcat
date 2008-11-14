VERSION=v1.0.3-beta
for i in $(find output/release/$VERSION -name "*.zip" -o -name "*.tar.gz"); do
  echo Signing $i
  echo $1|gpg --passphrase-fd 0 -a -b $i
done
