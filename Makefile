#run make run from project root
run:
	docker run -d --name foodbot -v "$(pwd)/data:/app/data" xhrome/fooduploadingbot
stop:
	docker rm -f foodbot