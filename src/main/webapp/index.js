var app =  angular.module('app',[]);

app.filter('trustAsHtml', function($sce) {
    return function(html) {
        return $sce.trustAsHtml(html);
    };
});

app.controller('SearchCtrl', function($scope, $http) {
    $scope.url = '/search'; // The url of our search

    // The function that will be executed on button click (ng-click="search()")
    $scope.search = function() {

        // Create the http post request
        // the data holds the keywords
        // The request is a JSON request.
        $http.get($scope.url+"?journal=tourism_il&term="+$scope.keywords).
            success(function(data, status) {
                $scope.status = status;
                $scope.data = data;
                $scope.result = data; // Show result from server in our <pre></pre> element
                for (var i = 0; i < data.length; i++) {
                    if (data[i].date != null) {
                        data[i].formateddate = moment(data[i].date).format("MMMM Do YYYY");
                    }
                }
            })
            .
            error(function(data, status) {
                $scope.data = data || "Request failed";
                $scope.status = status;
            });
    };
});