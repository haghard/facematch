# How to run

Download https://resources.djl.ai/test-models/pytorch/face_feature.zip and unzip it to ./facenet


```

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-22.jdk/Contents/Home


sbt assembly && java --add-modules=jdk.incubator.vector -jar server/target/scala-2.13/facematch-0.2.jar

```

http://localhost:8080/



# Issues
https://github.com/davidsandberg/facenet/issues/106
https://github.com/davidsandberg/facenet/wiki/Validate-on-lfw


# links
https://blog.softwaremill.com/incorporating-facenet-into-play-framework-with-silhouette-authentication-4a8844849b62

# Face match in python using Facenet and their pretrained model

https://github.com/arunmandal53/facematch

https://arxiv.org/abs/1503.03832

http://ramok.tech/2018/01/18/java-autonomous-driving-car-detection/

http://ramok.tech/2018/02/24/java-art-generation-with-neural-style-transfer/

https://github.com/portable-scala/sbt-crossproject

https://github.com/jhuckaby/webcamjs/blob/master/demos/basic.html



## Image comparison

The actual comparison of the given images happens on the python side with facematch.py script. The script uses Tensorflow and stored pre-calculated [pre-trained-weights](https://github.com/arunmandal53/facematch) to check if the faces match each other. The result is the probability of a match translated into a Boolean value indicating if that number is below a hard-coded threshold value.


# License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0).